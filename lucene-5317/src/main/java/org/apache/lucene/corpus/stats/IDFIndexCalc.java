package org.apache.lucene.corpus.stats;
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
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 * Lucene-agnostic IDF calculator
 */

public class IDFIndexCalc extends IDFCalc {

  private final IndexReader reader;

  public IDFIndexCalc(IndexReader reader) {
    super(reader.numDocs());
    this.reader = reader;
  }


  /**
   * @param t
   * @return idf for a single term or {@link #UNSEEN_IDF} if term is not found in
   * index
   * @throws java.io.IOException
   */
  public double singleTermIDF(Term t) throws IOException {
    return getIDF(reader.docFreq(t));
  }

  /**
   * Splits s on whitespace and then sums idf for each subtoken. This is
   * equivalent to multiplying the probabilities or, calculating the probability
   * if each term is completely independent of the other. The result is an
   * upperbound on the actual idf of the phrase. This is fast to
   * compute and yields decent results in practice. For more exact IDF for
   * phrases, consider indexing ngrams.
   * <p/>
   * Make sure to remove stop words before calculating the IDF.
   * A stop word will have an actual DF of 0, which will
   * be converted to {@value #DEFAULT_UNSEEN_COUNT}.
   *
   * @param s
   * @param t
   * @return
   * @throws java.io.IOException
   */
  public double multiTermIDFSum(String s, Term t) throws IOException {

    double sum = 0.0;
    for (String termString : s.trim().split(" +")) {
      Term tmp = new Term(t.field(), termString);
      sum += getIDF(reader.docFreq(tmp));
    }
    return sum;
  }

  /**
   * @param s
   * @param t
   * @return double[] of length 2, stats[0] is the sum of the individual term idfs
   * and stats[1] is the minimum idf for the phrase
   * @throws java.io.IOException
   */
  public double[] multiTermIDF(String s, Term t) throws IOException {
    // be careful: must pre-analyze and divide subterms by whitespace!!!
    double[] stats = new double[]{0.0, Double.MAX_VALUE}; // sum, min df, ...
    for (String termString : s.trim().split(" +")) {
      Term tmp = new Term(t.field(), termString);
      int df = reader.docFreq(tmp);
      double idf = getIDF(df);
      stats[0] += idf;

      if (df < stats[1])
        stats[1] = df;
    }
    return stats;
  }

  public double[] multiTermStats(String s, String field) throws IOException {
    return multiTermIDF(s, new Term(field, ""));
  }
}
