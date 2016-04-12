package org.apache.lucene.search.concordance.charoffsets;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;


public class SpansCrawler {

  public static void crawl(SpanQuery query, Filter filter, IndexSearcher searcher,
                           DocTokenOffsetsVisitor visitor) throws IOException, TargetTokenNotFoundException {
    SpanWeight w = query.createWeight(searcher, false);
    if (filter == null) {
      for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {

        Spans spans = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
        if (spans == null) {
          continue;
        }
        boolean cont = visitLeafReader(ctx, spans, visitor);
        if (!cont) {
          break;
        }
      }
    } else {
      for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {
        DocIdSet filterSet = filter.getDocIdSet(ctx, ctx.reader().getLiveDocs());
        if (filterSet == null) {
          return;
        }

        Spans spans = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
        if (spans == null) {
          continue;
        }
        DocIdSetIterator filterItr = filterSet.iterator();
        if (filterItr == null) {
          continue;
        }
        boolean cont = visitLeafReader(ctx, spans, filterItr, visitor);
        if (!cont) {
          break;
        }
      }
    }
  }

  static boolean visitLeafReader(LeafReaderContext leafCtx,
                                     Spans spans, DocIdSetIterator filterItr, DocTokenOffsetsVisitor visitor) throws IOException, TargetTokenNotFoundException {
    int filterDoc = -1;
    int spansDoc = spans.nextDoc();
    while (true) {
      if (spansDoc == DocIdSetIterator.NO_MORE_DOCS) {
        break;
      }
      filterDoc = filterItr.advance(spansDoc);
      if (filterDoc == DocIdSetIterator.NO_MORE_DOCS) {
        break;
      } else if (filterDoc > spansDoc) {
        while (spansDoc <= filterDoc) {
          spansDoc = spans.nextDoc();
          if (spansDoc == filterDoc) {
            boolean cont = visit(leafCtx, spans, visitor);
            if (! cont) {
              return false;
            }

          } else {
            continue;
          }
        }
      } else if (filterDoc == spansDoc) {
        boolean cont = visit(leafCtx, spans, visitor);
        if (! cont) {
          return false;
        }
        //then iterate spans
        spansDoc = spans.nextDoc();
      } else if (filterDoc < spansDoc) {
        throw new IllegalArgumentException("FILTER doc is < spansdoc!!!");
      } else {
        throw new IllegalArgumentException("Something horrible happened");
      }
    }
    return true;
  }

  static boolean visitLeafReader(LeafReaderContext leafCtx,
                                        Spans spans,
                                        DocTokenOffsetsVisitor visitor) throws IOException, TargetTokenNotFoundException {
    while (spans.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
      boolean cont = visit(leafCtx, spans, visitor);
      if (! cont) {
        return false;
      }
    }
    return true;
  }


  static boolean visit(LeafReaderContext leafCtx, Spans spans, DocTokenOffsetsVisitor visitor) throws IOException, TargetTokenNotFoundException {
    Document document = leafCtx.reader().document(spans.docID(), visitor.getFields());
    DocTokenOffsets offsets = visitor.getDocTokenOffsets();
    offsets.reset(leafCtx.docBase, spans.docID(), document);
    while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
      offsets.addOffset(spans.startPosition(), spans.endPosition());
    }
    return visitor.visit(offsets);
  }

}
