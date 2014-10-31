package org.apache.lucene.search.spans;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.FilteredQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

public class SimpleSpanQueryConverter {
  /**
   * Converts a regular query to a {@link org.apache.lucene.search.spans.SpanQuery} for use in a highlighter.
   * Because of subtle differences in {@link org.apache.lucene.search.spans.SpanQuery} and {@link org.apache.lucene.search.Query}, this
   * {@link org.apache.lucene.search.spans.SpanQuery} will not necessarily return the same documents as the
   * initial Query. For example, the generated SpanQuery will not include
   * clauses of type BooleanClause.Occur.MUST_NOT. Also, the
   * {@link org.apache.lucene.search.spans.SpanQuery} will only cover a single field, whereas the {@link org.apache.lucene.search.Query}
   * might contain multiple fields.
   * <p/>
   * Returns an empty SpanQuery if the {@link org.apache.lucene.search.Query} is a class that
   * is handled, but for some reason can't be converted from a {@link org.apache.lucene.search.Query} to a
   * {@link org.apache.lucene.search.spans.SpanQuery}. This can happen for many reasons: e.g. if the Query
   * contains no terms in the requested "field" or the Query is a MatchAllDocsQuery.
   * <p/>
   * Throws IllegalArgumentException if the Query is a class that is 
   * is not yet handled.
   * <p/>
   * This class does not rewrite the SpanQuery before returning it.
   * Clients are required to rewrite if necessary.
   * <p/>
   * Much of this code is copied directly from
   * oal.search.highlight.WeightedSpanTermExtractor. There are some subtle
   * differences.
   * 
   * @param field single field to extract SpanQueries for 
   * @param query query to convert
   * @return SpanQuery for use in highlighting; can return empty SpanQuery
   * @throws java.io.IOException, IllegalArgumentException
   */
  public SpanQuery convert(String field, Query query) throws IOException {
    /*
     * copied nearly verbatim from
     * org.apache.lucene.search.highlight.WeightedSpanTermExtractor 
     * TODO:refactor to avoid duplication of code if possible. 
     * Beware: there are some subtle differences.
     */
    if (query instanceof SpanQuery) {
      SpanQuery sq = (SpanQuery) query;
      if (sq.getField().equals(field)) {
        return (SpanQuery) query;
      } else {
        return getEmptySpanQuery();
      }
    } else if (query instanceof BooleanQuery) {
      BooleanClause[] queryClauses = ((BooleanQuery) query).getClauses();
      List<SpanQuery> spanQs = new ArrayList<SpanQuery>();
      for (int i = 0; i < queryClauses.length; i++) {
        if (!queryClauses[i].isProhibited()) {
          tryToAdd(field, convert(field, queryClauses[i].getQuery()), spanQs);
        }
      }
      if (spanQs.size() == 0) {
        return getEmptySpanQuery();
      } else if (spanQs.size() == 1) {
        return spanQs.get(0);
      } else {
        return new SpanOrQuery(spanQs.toArray(new SpanQuery[spanQs.size()]));
      }
    } else if (query instanceof PhraseQuery) {
      PhraseQuery phraseQuery = ((PhraseQuery) query);

      Term[] phraseQueryTerms = phraseQuery.getTerms();
      if (phraseQueryTerms.length == 0) {
        return getEmptySpanQuery();
      } else if (!phraseQueryTerms[0].field().equals(field)) {
        return getEmptySpanQuery();
      }
      SpanQuery[] clauses = new SpanQuery[phraseQueryTerms.length];
      for (int i = 0; i < phraseQueryTerms.length; i++) {
        clauses[i] = new SpanTermQuery(phraseQueryTerms[i]);
      }
      int slop = phraseQuery.getSlop();
      int[] positions = phraseQuery.getPositions();
      // sum  position increments (>1) and add to slop
      if (positions.length > 0) {
        int lastPos = positions[0];
        int sz = positions.length;
        for (int i = 1; i < sz; i++) {
          int pos = positions[i];
          int inc = pos - lastPos-1;
          slop += inc;          
          lastPos = pos;
        }
      }

      boolean inorder = false;

      if (phraseQuery.getSlop() == 0) {
        inorder = true;
      }

      SpanNearQuery sp = new SpanNearQuery(clauses, slop, inorder);
      sp.setBoost(query.getBoost());
      return sp;
    } else if (query instanceof TermQuery) {
      TermQuery tq = (TermQuery) query;
      if (tq.getTerm().field().equals(field)) {
        return new SpanTermQuery(tq.getTerm());
      } else {
        return getEmptySpanQuery();
      }
    } else if (query instanceof FilteredQuery) {
      return convert(field, ((FilteredQuery) query).getQuery());
    } else if (query instanceof ConstantScoreQuery) {
      return convert(field, ((ConstantScoreQuery) query).getQuery());
    } else if (query instanceof DisjunctionMaxQuery) {
      List<SpanQuery> spanQs = new ArrayList<SpanQuery>();
      for (Iterator<Query> iterator = ((DisjunctionMaxQuery) query).iterator(); iterator
          .hasNext();) {
        tryToAdd(field, convert(field, iterator.next()), spanQs);
      }
      if (spanQs.size() == 0) {
        return getEmptySpanQuery();
      } else if (spanQs.size() == 1) {
        return spanQs.get(0);
      } else {
        return new SpanOrQuery(spanQs.toArray(new SpanQuery[spanQs.size()]));
      }
    } else if (query instanceof MatchAllDocsQuery) {
      return getEmptySpanQuery();
    } else if (query instanceof MultiPhraseQuery) {
    
      final MultiPhraseQuery mpq = (MultiPhraseQuery) query;
      final List<Term[]> termArrays = mpq.getTermArrays();
      //test for empty or wrong field
      if (termArrays.size() == 0) {
        return getEmptySpanQuery();
      } else if (termArrays.size() > 1) {
        Term[] ts = termArrays.get(0);
        if (ts.length > 0) {
          Term t = ts[0];
          if (!t.field().equals(field)) {
            return getEmptySpanQuery();
          }
        }
      }
      final int[] positions = mpq.getPositions();
      if (positions.length > 0) {

        int maxPosition = positions[positions.length - 1];
        for (int i = 0; i < positions.length - 1; ++i) {
          if (positions[i] > maxPosition) {
            maxPosition = positions[i];
          }
        }

        @SuppressWarnings("unchecked")
        final List<SpanQuery>[] disjunctLists = new List[maxPosition + 1];
        int distinctPositions = 0;

        for (int i = 0; i < termArrays.size(); ++i) {
          final Term[] termArray = termArrays.get(i);
          List<SpanQuery> disjuncts = disjunctLists[positions[i]];
          if (disjuncts == null) {
            disjuncts = (disjunctLists[positions[i]] = new ArrayList<SpanQuery>(
                termArray.length));
            ++distinctPositions;
          }
          for (int j = 0; j < termArray.length; ++j) {
            disjuncts.add(new SpanTermQuery(termArray[j]));
          }
        }

        int positionGaps = 0;
        int position = 0;
        final SpanQuery[] clauses = new SpanQuery[distinctPositions];
        for (int i = 0; i < disjunctLists.length; ++i) {
          List<SpanQuery> disjuncts = disjunctLists[i];
          if (disjuncts != null) {
            if (disjuncts.size() == 1){
              clauses[position++] = disjuncts.get(0);
            } else {
              clauses[position++] = new SpanOrQuery(
                disjuncts.toArray(new SpanQuery[disjuncts.size()]));
            }
          } else {
            ++positionGaps;
          }
        }

        final int slop = mpq.getSlop();
        final boolean inorder = (slop == 0);

        SpanNearQuery sp = new SpanNearQuery(clauses, slop + positionGaps,
            inorder);
        sp.setBoost(query.getBoost());
        return sp;
      }

    } else if (query instanceof MultiTermQuery) {
      return new SpanMultiTermQueryWrapper<MultiTermQuery>((MultiTermQuery)query);
    }
    return convertUnknownQuery(field, query);
  }

  private void tryToAdd(String field, SpanQuery q, List<SpanQuery> qs) {
    if (q == null || isEmptyQuery(q) || !q.getField().equals(field)) {
      return;
    }
    qs.add(q);
  }

  /**
   * Extend this to handle queries that are not currently handled.
   * Might consider extending SpanQueryConverter in the queries compilation unit;
   * that includes CommonTermsQuery.
   * 
   * In this class, this always throws an IllegalArgumentException
   * @param field field to convert
   * @param query query to convert
   * @return nothing.  Throws IllegalArgumentException
   */
  protected SpanQuery convertUnknownQuery(String field, Query query) {
   throw new IllegalArgumentException("SpanQueryConverter is unable to convert this class "+
       query.getClass().toString());
  }

  /**
   * 
   * @return an empty SpanQuery (SpanOrQuery with no cluases)
   */
  protected SpanQuery getEmptySpanQuery() {
    SpanQuery q = new SpanOrQuery(new SpanTermQuery[0]);
    return q;
  }

  /**
   * Is this a null or empty SpanQuery
   * @param q query to test
   * @return whether a null or empty SpanQuery
   */
  protected boolean isEmptyQuery(SpanQuery q) {
    if (q == null) {
      return true;
    }
    if (q instanceof SpanOrQuery) {
      SpanOrQuery soq = (SpanOrQuery)q;
      for (SpanQuery sq : soq.getClauses()) {
        if (! isEmptyQuery(sq)) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
}