package org.apache.lucene.search.concordance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SimpleSpanQueryConverter;
import org.apache.lucene.search.spans.SpanOrQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.junit.Test;

public class TestSpanQueryConverter {

  @Test
  public void testMultiTerm() throws IOException {
    //test to make sure multiterm returns empty query for different field
    String f1 = "f1";
    String f2 = "f2";
    Query q = new PrefixQuery(new Term(f1, "f*"));
    SimpleSpanQueryConverter c = new SimpleSpanQueryConverter();
    SpanQuery sq = c.convert(f2, q);
    assertTrue(sq instanceof SpanOrQuery);
    assertEquals(0, ((SpanOrQuery)sq).getClauses().length);
  }
}
