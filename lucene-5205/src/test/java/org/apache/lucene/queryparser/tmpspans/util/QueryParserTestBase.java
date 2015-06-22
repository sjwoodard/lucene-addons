package org.apache.lucene.queryparser.tmpspans.util;

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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.flexible.standard.CommonQueryParserConfiguration;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.automaton.Automata;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Base Test class for QueryParser subclasses, that implement the historical lucene queryparser behavior.
 * <p/>
 * This contains a number of concrete tests for that behavior.
 */
public abstract class QueryParserTestBase extends QueryParserTestCase {

  public void testCJK() throws Exception {
    // Test Ideographic Space - As wide as a CJK character cell (fullwidth)
    // used google to translate the word "term" to japanese -> 用語
    assertQueryEquals("term\u3000term\u3000term", null, "term\u0020term\u0020term");
    assertQueryEquals("用語\u3000用語\u3000用語", null, "用語\u0020用語\u0020用語");
  }

  public void testCJKTerm() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    BooleanQuery.Builder bqb = new BooleanQuery.Builder();
    bqb.add(new TermQuery(new Term("field", "中")), Occur.SHOULD);
    bqb.add(new TermQuery(new Term("field", "国")), Occur.SHOULD);

    assertEquals(bqb.build(), getQuery("中国", analyzer));
  }

  public void testCJKBoostedTerm() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    BooleanQuery.Builder bqb = new BooleanQuery.Builder();
    bqb.add(new TermQuery(new Term("field", "中")), Occur.SHOULD);
    bqb.add(new TermQuery(new Term("field", "国")), Occur.SHOULD);

    BooleanQuery expected = bqb.build();
    expected.setBoost(0.5f);

    assertEquals(expected, getQuery("中国^0.5", analyzer));
  }

  public void testCJKPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "中"));
    pqb.add(new Term("field", "国"));

    assertEquals(pqb.build(), getQuery("\"中国\"", analyzer));
  }

  public void testCJKBoostedPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "中"));
    pqb.add(new Term("field", "国"));
    PhraseQuery expected = pqb.build();
    expected.setBoost(0.5f);

    assertQueryEquals(expected, getQuery("\"中国\"^0.5", analyzer));
  }

  public void testCJKSloppyPhrase() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "中"));
    pqb.add(new Term("field", "国"));
    pqb.setSlop(3);

    assertQueryEquals(pqb.build(), getQuery("\"中国\"~3", analyzer));
  }

  public void testAutoGeneratePhraseQueriesOn() throws Exception {
    // individual CJK chars as terms
    SimpleCJKAnalyzer analyzer = new SimpleCJKAnalyzer();

    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "中"));
    pqb.add(new Term("field", "国"));
    CommonQueryParserConfiguration qp = getParserConfig(analyzer);
    setAutoGeneratePhraseQueries(qp, true);
    assertQueryEquals(pqb.build(), getQuery("中国", qp));
  }

  public void testSimple() throws Exception {
    assertQueryEquals("term term term", null, "term term term");
    assertQueryEquals("türm term term", new MockAnalyzer(random()), "türm term term");
    assertQueryEquals("ümlaut", new MockAnalyzer(random()), "ümlaut");

    assertQueryEquals("a AND b", null, "+a +b");
    assertQueryEquals("(a AND b)", null, "+a +b");
    assertQueryEquals("c OR (a AND b)", null, "c (+a +b)");
    assertQueryEquals("a AND NOT b", null, "+a -b");
    assertQueryEquals("a AND -b", null, "+a -b");

    assertQueryEquals("a OR b", null, "a b");
    assertQueryEquals("a OR -b", null, "a -b");

    assertQueryEquals("+term -term term", null, "+term -term term");
    assertQueryEquals("foo:term AND field:anotherTerm", null,
        "+foo:term +anotherterm");
    assertInstanceOf(getQuery("a AND b"), BooleanQuery.class);
    assertInstanceOf(getQuery("hello"), TermQuery.class);
    assertInstanceOf(getQuery("\"hello there\""), PhraseQuery.class);

    assertQueryEquals("germ term^2.0", null, "germ term^2.0");
    assertQueryEquals("(term)^2.0", null, "term^2.0");
    assertQueryEquals("(germ term)^2.0", null, "(germ term)^2.0");
    assertQueryEquals("term^2.0", null, "term^2.0");
    assertQueryEquals("term^2", null, "term^2.0");
  }

  // FIXME: enhance MockAnalyzer to be able to support testing the empty string.

  public abstract void testDefaultOperator() throws Exception;

  // LUCENE-2566
  public void testOperatorVsWhitespace() throws Exception {
    // +,-,! should be directly adjacent to operand (i.e. not separated by whitespace) to be treated as an operator
    Analyzer a = new Analyzer() {
      @Override
      public TokenStreamComponents createComponents(String fieldName) {
        return new TokenStreamComponents(new MockTokenizer(MockTokenizer.WHITESPACE, false));
      }
    };
    assertQueryEquals("a - b", a, "a - b");
    assertQueryEquals("a + b", a, "a + b");
    assertQueryEquals("a ! b", a, "a ! b");
  }

  public void testPunct() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    assertQueryEquals("a&b", a, "a&b");
    assertQueryEquals("a&&b", a, "a&&b");
    assertQueryEquals(".NET", a, ".NET");
  }

  public void testSlop() throws Exception {
    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "term"));
    pqb.add(new Term("field", "germ"));
    pqb.setSlop(2);
    PhraseQuery q = pqb.build();
    assertQueryEquals(q, getQuery("\"term germ\"~2"));

    q.setBoost(2.0f);
    assertQueryEquals(q, getQuery("\"term germ\"~2^2"));

    assertQueryEquals("\"term\"~2", null, "term");
    assertQueryEquals("\" \"~2 germ", null, "germ");
  }

  public void testNumber() throws Exception {
    // The numbers go away because SimpleAnalyzer ignores them
    assertQueryEquals("3", null, "");
    assertQueryEquals("term 1.0 1 2", null, "term");
    assertQueryEquals("term term1 term2", null, "term term term");

    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    assertQueryEquals("3", a, "3");
    assertQueryEquals("term 1.0 1 2", a, "term 1.0 1 2");
    assertQueryEquals("term term1 term2", a, "term term1 term2");
  }

  public void testWildcard() throws Exception {
    assertQueryEquals("term*", null, "term*");
    assertQueryEquals("term*^2", null, "term*^2.0");
    assertQueryEquals("term~", null, "term~2");
    assertQueryEquals("term~1", null, "term~1");
    assertQueryEquals("term~0.7", null, "term~1");
    assertQueryEquals("term~^3", null, "term~2^3.0");

    assertQueryEquals("term*germ", null, "term*germ");
    assertQueryEquals("term*germ^3", null, "term*germ^3.0");

    assertInstanceOf(getQuery("term*"), PrefixQuery.class);
    assertInstanceOf(getQuery("term*^2"), PrefixQuery.class);
    assertInstanceOf(getQuery("term~"), FuzzyQuery.class);
    assertInstanceOf(getQuery("term~0.7"), FuzzyQuery.class);
    assertFuzzyQueryEquals("field", "term", 1, FuzzyQuery.defaultPrefixLength, getQuery("term~0.7"));
    assertFuzzyQueryEquals("field", "term", 2, FuzzyQuery.defaultPrefixLength, getQuery("term~"));

    assertParseException("term~1.1"); // value > 1, throws exception

    assertInstanceOf(getQuery("term*germ"), WildcardQuery.class);

    // Tests to see that wild card terms are (or are not) properly
    // lower-cased with propery parser configuration

    // First prefix queries:
    // by default, convert to lowercase:
    assertWildcardQueryEquals("Term*", true, "term*");
    // explicitly set lowercase:
    assertWildcardQueryEquals("term*", true, "term*");
    assertWildcardQueryEquals("Term*", true, "term*");
    assertWildcardQueryEquals("TERM*", true, "term*");
    // explicitly disable lowercase conversion:
    assertWildcardQueryEquals("term*", false, "term*");
    assertWildcardQueryEquals("Term*", false, "Term*");
    assertWildcardQueryEquals("TERM*", false, "TERM*");
    // Then 'full' wildcard queries:
    // by default, convert to lowercase:
    assertWildcardQueryEquals("Te?m", "te?m");
    // explicitly set lowercase:
    assertWildcardQueryEquals("te?m", true, "te?m");
    assertWildcardQueryEquals("Te?m", true, "te?m");
    assertWildcardQueryEquals("TE?M", true, "te?m");
    assertWildcardQueryEquals("Te?m*gerM", true, "te?m*germ");
    // explicitly disable lowercase conversion:
    assertWildcardQueryEquals("te?m", false, "te?m");
    assertWildcardQueryEquals("Te?m", false, "Te?m");
    assertWildcardQueryEquals("TE?M", false, "TE?M");
    assertWildcardQueryEquals("Te?m*gerM", false, "Te?m*gerM");
    // Fuzzy queries:
    assertWildcardQueryEquals("Term~", "term~2");
    assertWildcardQueryEquals("Term~", true, "term~2");
    assertWildcardQueryEquals("Term~", false, "Term~2");
    // Range queries:
    assertWildcardQueryEquals("[A TO C]", "[a TO c]");
    assertWildcardQueryEquals("[A TO C]", true, "[a TO c]");
    assertWildcardQueryEquals("[A TO C]", false, "[A TO C]");
    // Test suffix queries: first disallow
    try {
      assertWildcardQueryEquals("*Term", true, "*term");
      fail("didn't get expected exception");
    } catch (Exception pe) {
      assertTrue(isQueryParserException(pe));
    }

    try {
      assertWildcardQueryEquals("?Term", true, "?term");
      fail("didn't get expected exception");
    } catch (Exception pe) {
      assertTrue(isQueryParserException(pe));
    }

    // Test suffix queries: then allow
    assertWildcardQueryEquals("*Term", true, "*term", true);
    assertWildcardQueryEquals("?Term", true, "?term", true);
  }

  public void testLeadingWildcardType() throws Exception {
    CommonQueryParserConfiguration cqpC = getParserConfig(null);
    cqpC.setAllowLeadingWildcard(true);
    assertInstanceOf(getQuery("t*erm*", cqpC), WildcardQuery.class);
    assertInstanceOf(getQuery("?term*", cqpC), WildcardQuery.class);
    assertInstanceOf(getQuery("*term*", cqpC), WildcardQuery.class);
  }

  public void testQPA() throws Exception {
    assertQueryEquals("term term^3.0 term", qpAnalyzer, "term term^3.0 term");
    assertQueryEquals("term stop^3.0 term", qpAnalyzer, "term term");

    assertQueryEquals("term term term", qpAnalyzer, "term term term");
    assertQueryEquals("term +stop term", qpAnalyzer, "term term");
    assertQueryEquals("term -stop term", qpAnalyzer, "term term");

    assertQueryEquals("drop AND (stop) AND roll", qpAnalyzer, "+drop +roll");
    assertQueryEquals("term +(stop) term", qpAnalyzer, "term term");
    assertQueryEquals("term -(stop) term", qpAnalyzer, "term term");

    assertQueryEquals("drop AND stop AND roll", qpAnalyzer, "+drop +roll");

    assertQueryEquals("stop^3", qpAnalyzer, "");
    assertQueryEquals("stop", qpAnalyzer, "");
    assertQueryEquals("(stop)^3", qpAnalyzer, "");
    assertQueryEquals("((stop))^3", qpAnalyzer, "");
    assertQueryEquals("(stop^3)", qpAnalyzer, "");
    assertQueryEquals("((stop)^3)", qpAnalyzer, "");
    assertQueryEquals("(stop)", qpAnalyzer, "");
    assertQueryEquals("((stop))", qpAnalyzer, "");
    assertInstanceOf(getQuery("term term term", qpAnalyzer), BooleanQuery.class);
    assertInstanceOf(getQuery("term +stop", qpAnalyzer), TermQuery.class);

  }

  public void testRange() throws Exception {
    assertQueryEquals("[ a TO z]", null, "[a TO z]");
    assertQueryEquals("[ a TO z}", null, "[a TO z}");
    assertQueryEquals("{ a TO z]", null, "{a TO z]");


    // test open ranges
    assertQueryEquals("[ a TO * ]", null, "[a TO *]");
    assertQueryEquals("[ * TO z ]", null, "[* TO z]");
    assertQueryEquals("[ * TO * ]", null, "[* TO *]");

    // mixing exclude and include bounds
    assertQueryEquals("{ a TO z ]", null, "{a TO z]");
    assertQueryEquals("[ a TO z }", null, "[a TO z}");
    assertQueryEquals("{ a TO * ]", null, "{a TO *]");
    assertQueryEquals("[ * TO z }", null, "[* TO z}");

    assertQueryEquals("[ a TO z ]", null, "[a TO z]");
    assertQueryEquals("{ a TO z}", null, "{a TO z}");
    assertQueryEquals("{ a TO z }", null, "{a TO z}");
    assertQueryEquals("{ a TO z }^2.0", null, "{a TO z}^2.0");

    assertQueryEquals("[* TO Z]", null, "[* TO z]");
    assertQueryEquals("[A TO *]", null, "[a TO *]");
    assertQueryEquals("[* TO *]", null, "[* TO *]");
  }

  public void testRangeWithPhrase() throws Exception {
    assertQueryEquals("[\\* TO \"*\"]", null, "[\\* TO \\*]");
    assertQueryEquals("[\"*\" TO *]", null, "[\\* TO *]");
  }

  public void testDateRange() throws Exception {
    String startDate = getLocalizedDate(2002, 1, 1);
    String endDate = getLocalizedDate(2002, 1, 4);
    // we use the default Locale/TZ since LuceneTestCase randomizes it
    Calendar endDateExpected = new GregorianCalendar(TimeZone.getDefault(), Locale.getDefault());
    endDateExpected.clear();
    endDateExpected.set(2002, 1, 4, 23, 59, 59);
    endDateExpected.set(Calendar.MILLISECOND, 999);
    final String defaultField = "default";
    final String monthField = "month";
    final String hourField = "hour";
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    CommonQueryParserConfiguration qp = getParserConfig(a);

    // set a field specific date resolution
    setDateResolution(qp, monthField, DateTools.Resolution.MONTH);

    // set default date resolution to MILLISECOND
    qp.setDateResolution(DateTools.Resolution.MILLISECOND);

    // set second field specific date resolution    
    setDateResolution(qp, hourField, DateTools.Resolution.HOUR);

    // for this field no field specific date resolution has been set,
    // so verify if the default resolution is used
    assertDateRangeQueryEquals(qp, defaultField, startDate, endDate,
        endDateExpected.getTime(), DateTools.Resolution.MILLISECOND);

    // verify if field specific date resolutions are used for these two fields
    assertDateRangeQueryEquals(qp, monthField, startDate, endDate,
        endDateExpected.getTime(), DateTools.Resolution.MONTH);

    assertDateRangeQueryEquals(qp, hourField, startDate, endDate,
        endDateExpected.getTime(), DateTools.Resolution.HOUR);
  }

  public void testEscaped() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);

    assertQueryEquals("\\[brackets", a, "[brackets");
    assertQueryEquals("\\[brackets", null, "brackets");
    assertQueryEquals("\\\\", a, "\\");
    assertQueryEquals("\\+blah", a, "+blah");
    assertQueryEquals("\\(blah", a, "(blah");

    assertQueryEquals("\\-blah", a, "-blah");
    assertQueryEquals("\\!blah", a, "!blah");
    assertQueryEquals("\\{blah", a, "{blah");
    assertQueryEquals("\\}blah", a, "}blah");
    assertQueryEquals("\\:blah", a, ":blah");
    assertQueryEquals("\\^blah", a, "^blah");
    assertQueryEquals("\\[blah", a, "[blah");
    assertQueryEquals("\\]blah", a, "]blah");
    assertQueryEquals("\\\"blah", a, "\"blah");
    assertQueryEquals("\\(blah", a, "(blah");
    assertQueryEquals("\\)blah", a, ")blah");
    assertQueryEquals("\\~blah", a, "~blah");
    assertQueryEquals("\\*blah", a, "*blah");
    assertQueryEquals("\\?blah", a, "?blah");
    assertQueryEquals("foo \\&\\& bar", a, "foo && bar");
    assertQueryEquals("foo \\|| bar", a, "foo || bar");
    assertQueryEquals("foo \\AND bar", a, "foo AND bar");

    assertQueryEquals("\\a", a, "a");

    assertQueryEquals("a\\-b:c", a, "a-b:c");
    assertQueryEquals("a\\+b:c", a, "a+b:c");
    assertQueryEquals("a\\:b:c", a, "a:b:c");
    assertQueryEquals("a\\\\b:c", a, "a\\b:c");

    assertQueryEquals("a:b\\-c", a, "a:b-c");
    assertQueryEquals("a:b\\+c", a, "a:b+c");
    assertQueryEquals("a:b\\:c", a, "a:b:c");
    assertQueryEquals("a:b\\\\c", a, "a:b\\c");

    assertQueryEquals("a:b\\-c*", a, "a:b-c*");
    assertQueryEquals("a:b\\+c*", a, "a:b+c*");
    assertQueryEquals("a:b\\:c*", a, "a:b:c*");

    assertQueryEquals("a:b\\\\c*", a, "a:b\\c*");

    assertQueryEquals("a:b\\-c~", a, "a:b-c~2");
    assertQueryEquals("a:b\\+c~", a, "a:b+c~2");
    assertQueryEquals("a:b\\:c~", a, "a:b:c~2");
    assertQueryEquals("a:b\\\\c~", a, "a:b\\c~2");

    assertQueryEquals("[ a\\- TO a\\+ ]", null, "[a- TO a+]");
    assertQueryEquals("[ a\\: TO a\\~ ]", null, "[a: TO a~]");
    assertQueryEquals("[ a\\\\ TO a\\* ]", null, "[a\\ TO a*]");


    assertQueryEquals("a\\\\\\+b", a, "a\\+b");

    assertQueryEquals("a \\\"b c\\\" d", a, "a \"b c\" d");

    assertQueryEquals("c\\:\\\\temp\\\\\\~foo.txt", a, "c:\\temp\\~foo.txt");

    assertParseException("XY\\"); // there must be a character after the escape char

    // test unicode escaping
    assertQueryEquals("a\\u0062c", a, "abc");
    assertQueryEquals("XY\\u005a", a, "XYZ");
    assertQueryEquals("XY\\u005A", a, "XYZ");

    assertParseException("XY\\u005G");  // test non-hex character in escaped unicode sequence
    assertParseException("XY\\u005");   // test incomplete escaped unicode sequence

    // Tests bug LUCENE-800
    assertQueryEquals("(item:\\\\ item:ABCD\\\\)", a, "item:\\ item:ABCD\\");
    assertParseException("(item:\\\\ item:ABCD\\\\))"); // unmatched closing paranthesis 
    assertQueryEquals("\\*", a, "*");
    assertQueryEquals("\\\\", a, "\\");  // escaped backslash

    assertParseException("\\"); // a backslash must always be escaped

    // LUCENE-1189
    assertQueryEquals("(\"a\\\\\") or (\"b\")", a, "a\\ or b");
  }

  public void testEscapedVsQuestionMarkAsWildcard() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    assertQueryEquals("a:b\\-?c", a, "a:b\\-?c");
    assertQueryEquals("a:b\\+?c", a, "a:b\\+?c");
    assertQueryEquals("a:b\\:?c", a, "a:b\\:?c");

    assertQueryEquals("a:b\\\\?c", a, "a:b\\\\?c");
  }

  public void testQueryStringEscaping() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);

    assertEscapedQueryEquals("a-b:c", a, "a\\-b\\:c");
    assertEscapedQueryEquals("a+b:c", a, "a\\+b\\:c");
    assertEscapedQueryEquals("a:b:c", a, "a\\:b\\:c");
    assertEscapedQueryEquals("a\\b:c", a, "a\\\\b\\:c");

    assertEscapedQueryEquals("a:b-c", a, "a\\:b\\-c");
    assertEscapedQueryEquals("a:b+c", a, "a\\:b\\+c");
    assertEscapedQueryEquals("a:b:c", a, "a\\:b\\:c");
    assertEscapedQueryEquals("a:b\\c", a, "a\\:b\\\\c");

    assertEscapedQueryEquals("a:b-c*", a, "a\\:b\\-c\\*");
    assertEscapedQueryEquals("a:b+c*", a, "a\\:b\\+c\\*");
    assertEscapedQueryEquals("a:b:c*", a, "a\\:b\\:c\\*");

    assertEscapedQueryEquals("a:b\\\\c*", a, "a\\:b\\\\\\\\c\\*");

    assertEscapedQueryEquals("a:b-?c", a, "a\\:b\\-\\?c");
    assertEscapedQueryEquals("a:b+?c", a, "a\\:b\\+\\?c");
    assertEscapedQueryEquals("a:b:?c", a, "a\\:b\\:\\?c");

    assertEscapedQueryEquals("a:b?c", a, "a\\:b\\?c");

    assertEscapedQueryEquals("a:b-c~", a, "a\\:b\\-c\\~");
    assertEscapedQueryEquals("a:b+c~", a, "a\\:b\\+c\\~");
    assertEscapedQueryEquals("a:b:c~", a, "a\\:b\\:c\\~");
    assertEscapedQueryEquals("a:b\\c~", a, "a\\:b\\\\c\\~");

    assertEscapedQueryEquals("[ a - TO a+ ]", null, "\\[ a \\- TO a\\+ \\]");
    assertEscapedQueryEquals("[ a : TO a~ ]", null, "\\[ a \\: TO a\\~ \\]");
    assertEscapedQueryEquals("[ a\\ TO a* ]", null, "\\[ a\\\\ TO a\\* \\]");

    // LUCENE-881
    assertEscapedQueryEquals("|| abc ||", a, "\\|\\| abc \\|\\|");
    assertEscapedQueryEquals("&& abc &&", a, "\\&\\& abc \\&\\&");
  }

  public void testTabNewlineCarriageReturn() throws Exception {
    assertQueryEqualsDOA("+weltbank +worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("+weltbank\n+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \n+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \n +worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("+weltbank\r+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r +worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("+weltbank\r\n+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r\n+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r\n +worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \r \n +worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("+weltbank\t+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \t+worlbank", null, "+weltbank +worlbank");
    assertQueryEqualsDOA("weltbank \t +worlbank", null, "+weltbank +worlbank");
  }

  public void testSimpleDAO() throws Exception {
    assertQueryEqualsDOA("term term term", null, "+term +term +term");
    assertQueryEqualsDOA("term +term term", null, "+term +term +term");
    assertQueryEqualsDOA("term term +term", null, "+term +term +term");
    assertQueryEqualsDOA("term +term +term", null, "+term +term +term");
    assertQueryEqualsDOA("-term term term", null, "-term +term +term");
  }

  public void testBoost() throws Exception {
    CharacterRunAutomaton stopWords = new CharacterRunAutomaton(Automata.makeString("on"));
    Analyzer oneStopAnalyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, stopWords);
    CommonQueryParserConfiguration qp = getParserConfig(oneStopAnalyzer);
    Query q = getQuery("on^1.0", qp);
    assertNotNull(q);
    q = getQuery("\"hello\"^2.0", qp);
    assertNotNull(q);
    assertEquals(q.getBoost(), (float) 2.0, (float) 0.5);
    q = getQuery("hello^2.0", qp);
    assertNotNull(q);
    assertEquals(q.getBoost(), (float) 2.0, (float) 0.5);
    q = getQuery("\"on\"^1.0", qp);
    assertNotNull(q);

    Analyzer a2 = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET);
    CommonQueryParserConfiguration qp2 = getParserConfig(a2);
    q = getQuery("the^3", qp2);
    // "the" is a stop word so the result is an empty query:
    assertNotNull(q);
    assertEmpty(q);
    assertEquals(1.0f, q.getBoost(), 0.01f);
  }

  public void testException() throws Exception {
    assertParseException("\"some phrase");
    assertParseException("(foo bar");
    assertParseException("foo bar))");
    assertParseException("field:term:with:colon some more terms");
    assertParseException("(sub query)^5.0^2.0 plus more");
    assertParseException("secret AND illegal) AND access:confidential");
  }

  public void testBooleanQuery() throws Exception {
    BooleanQuery.setMaxClauseCount(2);
    Analyzer purWhitespaceAnalyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    assertParseException("one two three", purWhitespaceAnalyzer);
  }

  /**
   * This test differs from TestPrecedenceQueryParser
   */
  public void testPrecedence() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    Query query1 = getQuery("A AND B OR C AND D", qp);
    Query query2 = getQuery("+A +B +C +D", qp);
    assertEquals(query1, query2);
  }

  public abstract void testStarParsing() throws Exception;

  public void testEscapedWildcard() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    WildcardQuery q = new WildcardQuery(new Term("field", "foo\\?ba?r"));
    assertQueryEquals(q, getQuery("foo\\?ba?r", qp));
  }

  public void testRegexps() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    RegexpQuery q = new RegexpQuery(new Term("field", "[a-z][123]"));
    assertQueryEquals(q, getQuery("/[a-z][123]/", qp));
    qp.setLowercaseExpandedTerms(true);
    assertQueryEquals(q, getQuery("/[A-Z][123]/", qp));
    q.setBoost(0.5f);
    assertQueryEquals(q, getQuery("/[A-Z][123]/^0.5", qp));
    qp.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    q.setRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    assertInstanceOf(getQuery("/[A-Z][123]/^0.5", qp), RegexpQuery.class);
    assertQueryEquals(q, getQuery("/[A-Z][123]/^0.5", qp));
    qp.setMultiTermRewriteMethod(MultiTermQuery.CONSTANT_SCORE_REWRITE);

    Query escaped2 = new RegexpQuery(new Term("field", "[a-z]\\*[123]"));
    assertQueryEquals(escaped2, getQuery("/[a-z]\\*[123]/", qp));


    Query re = new RegexpQuery(new Term("field", "http.*"));
    assertQueryEquals(re, getQuery("field:/http.*/", qp));
    assertQueryEquals(re, getQuery("/http.*/", qp));

    re = new RegexpQuery(new Term("field", "http~0.5"));
    assertQueryEquals(re, getQuery("field:/http~0.5/", qp));
    assertQueryEquals(re, getQuery("/http~0.5/", qp));

    re = new RegexpQuery(new Term("field", "boo"));
    assertQueryEquals(re, getQuery("field:/boo/", qp));
    assertQueryEquals(re, getQuery("/boo/", qp));

    assertQueryEquals(new TermQuery(new Term("field", "/boo/")), getQuery("\"/boo/\"", qp));
    assertQueryEquals(new TermQuery(new Term("field", "/boo/")), getQuery("\\/boo\\/", qp));

    BooleanQuery.Builder bqb = new BooleanQuery.Builder();
    bqb.add(new RegexpQuery(new Term("field", "foo")), Occur.SHOULD);
    bqb.add(new RegexpQuery(new Term("field", "bar")), Occur.SHOULD);
    BooleanQuery two = bqb.build();
    assertQueryEquals(two, getQuery("field:/foo/ field:/bar/", qp));
    assertQueryEquals(two, getQuery("/foo/ /bar/", qp));
  }

  public void testStopwords() throws Exception {
    CharacterRunAutomaton stopSet = new CharacterRunAutomaton(new RegExp("the|foo").toAutomaton());
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, stopSet));
    Query result = getQuery("field:the OR field:foo", qp);
    assertNotNull("result is null and it shouldn't be", result);
    assertInstanceOf(result, BooleanQuery.class);
    assertEmpty(result);
    result = getQuery("field:woo OR field:the", qp);
    assertNotNull("result is null and it shouldn't be", result);
    assertInstanceOf(result, TermQuery.class);
    result = getQuery("(fieldX:xxxxx OR fieldy:xxxxxxxx)^2 AND (fieldx:the OR fieldy:foo)", qp);
    assertNotNull("result is null and it shouldn't be", result);
    assertInstanceOf(result, BooleanQuery.class);
    if (VERBOSE) System.out.println("Result: " + result);
    assertTrue(((BooleanQuery) result).clauses().size() + " does not equal: " + 2, ((BooleanQuery) result).clauses().size() == 2);
  }

  public void testPositionIncrement() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET));
    qp.setEnablePositionIncrements(true);
    String qtxt = "\"the words in poisitions pos02578 are stopped in this phrasequery\"";
    //               0         2                      5           7  8
    int expectedPositions[] = {1, 3, 4, 6, 9};
    PhraseQuery pq = (PhraseQuery) getQuery(qtxt, qp);
    Term t[] = pq.getTerms();
    int pos[] = pq.getPositions();
    for (int i = 0; i < t.length; i++) {
      assertEquals("term " + i + " = " + t[i] + " has wrong term-position!", expectedPositions[i], pos[i]);
    }
  }

  public void testMatchAllDocs() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    assertEquals(new MatchAllDocsQuery(), getQuery("*:*", qp));
    assertEquals(new MatchAllDocsQuery(), getQuery("(*:*)", qp));
    BooleanQuery bq = (BooleanQuery) getQuery("+*:* -*:*", qp);
    assertInstanceOf(bq.clauses().get(0).getQuery(), MatchAllDocsQuery.class);
    assertInstanceOf(bq.clauses().get(1).getQuery(), MatchAllDocsQuery.class);
  }

  // LUCENE-2002: make sure defaults for StandardAnalyzer's
  // enableStopPositionIncr & QueryParser's enablePosIncr
  // "match"
  public void testPositionIncrements() throws Exception {
    Directory dir = newDirectory();
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET);
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(a));
    Document doc = new Document();
    doc.add(newTextField("field", "the wizard of ozzy", Field.Store.NO));
    w.addDocument(doc);
    IndexReader r = DirectoryReader.open(w, true);
    w.close();
    IndexSearcher s = newSearcher(r);

    Query q = getQuery("\"wizard of ozzy\"", a);
    assertEquals(1, s.search(q, 1).totalHits);
    r.close();
    dir.close();
  }

  public abstract void testNewFieldQuery() throws Exception;

  public void testCollatedRange() throws Exception {
    CommonQueryParserConfiguration qp = getParserConfig(new MockCollationAnalyzer());
    setAnalyzeRangeTerms(qp, true);
    Query expected = TermRangeQuery.newStringRange(getDefaultField(), "collatedabc", "collateddef", true, true);
    Query actual = getQuery("[abc TO def]", qp);
    assertQueryEquals(expected, actual);
  }

  public void testDistanceAsEditsParsing() throws Exception {
    FuzzyQuery expected = new FuzzyQuery(new Term("field", "foobar"), 2);
    assertQueryEquals(expected, getQuery("foobar~2", new MockAnalyzer(random())));
  }

  public void testPhraseQueryToString() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true, MockTokenFilter.ENGLISH_STOPSET);
    CommonQueryParserConfiguration qp = getParserConfig(analyzer);
    qp.setEnablePositionIncrements(true);
    PhraseQuery q = (PhraseQuery) getQuery("\"this hi this is a test is\"", qp);
    assertEquals("field:\"? hi ? ? ? test\"", q.toString());
  }

  public void testParseWildcardAndPhraseQueries() throws Exception {
    String field = "content";
    String oldDefaultField = getDefaultField();
    setDefaultField(field);
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random()));
    qp.setAllowLeadingWildcard(true);

    String prefixQueries[][] = {
        {"a*", "ab*", "abc*",},
        {"h*", "hi*", "hij*", "\\\\7*"},
        {"o*", "op*", "opq*", "\\\\\\\\*"},
    };

    String wildcardQueries[][] = {
        {"*a*", "*ab*", "*abc**", "ab*e*", "*g?", "*f?1", "abc**"},
        {"*h*", "*hi*", "*hij**", "hi*k*", "*n?", "*m?1", "hij**"},
        {"*o*", "*op*", "*opq**", "op*q*", "*u?", "*t?1", "opq**"},
    };

    // test queries that must be prefix queries
    for (int i = 0; i < prefixQueries.length; i++) {
      for (int j = 0; j < prefixQueries[i].length; j++) {
        String queryString = prefixQueries[i][j];
        Query q = getQuery(queryString, qp);
        assertInstanceOf(q, PrefixQuery.class);
      }
    }

    // test queries that must be wildcard queries
    for (int i = 0; i < wildcardQueries.length; i++) {
      for (int j = 0; j < wildcardQueries[i].length; j++) {
        String qtxt = wildcardQueries[i][j];
        Query q = getQuery(qtxt, qp);
        assertInstanceOf(q, WildcardQuery.class);
      }
    }
    setDefaultField(oldDefaultField);
  }

  public void testPhraseQueryPositionIncrements() throws Exception {
    CharacterRunAutomaton stopStopList =
        new CharacterRunAutomaton(new RegExp("[sS][tT][oO][pP]").toAutomaton());

    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false, stopStopList));
    qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false, stopStopList));
    qp.setEnablePositionIncrements(true);

    PhraseQuery.Builder pqb = new PhraseQuery.Builder();
    pqb.add(new Term("field", "1"));
    pqb.add(new Term("field", "2"), 2);
    assertEquals(pqb.build(), getQuery("\"1 stop 2\"", qp));
  }

  public void testMatchAllQueryParsing() throws Exception {
    // test simple parsing of MatchAllDocsQuery
    String oldDefaultField = getDefaultField();
    setDefaultField("key");
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random()));
    assertEquals(new MatchAllDocsQuery(), getQuery(new MatchAllDocsQuery().toString(), qp));

    // test parsing with non-default boost
    MatchAllDocsQuery query = new MatchAllDocsQuery();
    query.setBoost(2.3f);
    assertQueryEquals(query, getQuery(query.toString(), qp));
    setDefaultField(oldDefaultField);
  }

  public void testNestedAndClausesFoo() throws Exception {
    String query = "(field1:[1 TO *] AND field1:[* TO 2]) AND field2:(z)";
    BooleanQuery.Builder childBuilder = new BooleanQuery.Builder();
    BooleanQuery.Builder bqb = new BooleanQuery.Builder();

    childBuilder.add(TermRangeQuery.newStringRange("field1", "1", null, true, true), Occur.MUST);
    childBuilder.add(TermRangeQuery.newStringRange("field1", null, "2", true, true), Occur.MUST);
    bqb.add(childBuilder.build(), Occur.MUST);
    bqb.add(new TermQuery(new Term("field2", "z")), Occur.MUST);
    assertEquals(bqb.build(), getQuery(query, new MockAnalyzer(random())));
  }

  //string query equality tests that have to be rewritten
  //if parser is generating a SpanQuery
  public void testParserSpecificQuery() throws Exception {

    //testSimple
    assertQueryEquals("a AND !b", null, "+a -b");
    assertQueryEquals("a && b", null, "+a +b");
    assertQueryEquals("a || b", null, "a b");
    assertQueryEquals("a OR !b", null, "a -b");

    assertQueryEquals("term AND \"phrase phrase\"", null,
        "+term +\"phrase phrase\"");
    assertQueryEquals("\"hello there\"", null, "\"hello there\"");
    assertQueryEquals("\"germ term\"^2.0", null, "\"germ term\"^2.0");
    assertQueryEquals("\"term germ\"^2", null, "\"term germ\"^2.0");

    assertQueryEquals("(foo OR bar) AND (baz OR boo)", null,
        "+(foo bar) +(baz boo)");
    assertQueryEquals("((a OR b) AND NOT c) OR d", null,
        "(+(a b) -c) d");
    assertQueryEquals("+(apple \"steve jobs\") -(foo bar baz)", null,
        "+(apple \"steve jobs\") -(foo bar baz)");
    assertQueryEquals("+title:(dog OR cat) -author:\"bob dole\"", null,
        "+(title:dog title:cat) -author:\"bob dole\"");


    //testRegexps
    CommonQueryParserConfiguration qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    Query escaped = new RegexpQuery(new Term("field", "[a-z]\\/[123]"));
    assertQueryEquals(escaped, getQuery("/[a-z]\\/[123]/", qp));

    BooleanQuery.Builder bqb = new BooleanQuery.Builder();
    bqb.add(new RegexpQuery(new Term("field", "[a-z]\\/[123]")), Occur.MUST);
    bqb.add(new TermQuery(new Term("path", "/etc/init.d/")), Occur.MUST);
    bqb.add(new TermQuery(new Term("field", "/etc/init[.]d/lucene/")), Occur.SHOULD);
    assertQueryEquals(bqb.build(), getQuery("/[a-z]\\/[123]/ AND path:\"/etc/init.d/\" OR \"/etc\\/init\\[.\\]d/lucene/\" ", qp));
    qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false));
    qp.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    assertEquals(MultiTermQuery.SCORING_BOOLEAN_REWRITE, ((RegexpQuery) getQuery("/[A-Z][123]/^0.5", qp)).getRewriteMethod());

    //testWildcard
    assertQueryEquals("term^3~", null, "term~2^3.0");


    //testRange
    assertEquals(MultiTermQuery.CONSTANT_SCORE_REWRITE, ((TermRangeQuery) getQuery("[ a TO z]")).getRewriteMethod());
    qp = getParserConfig(new MockAnalyzer(random(), MockTokenizer.SIMPLE, true));
    qp.setMultiTermRewriteMethod(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    assertEquals(MultiTermQuery.SCORING_BOOLEAN_REWRITE, ((TermRangeQuery) getQuery("[ a TO z]", qp)).getRewriteMethod());
    assertQueryEquals("[ a TO z] OR bar", null, "[a TO z] bar");
    assertQueryEquals("[ a TO z] AND bar", null, "+[a TO z] +bar");
    assertQueryEquals("( bar blar { a TO z}) ", null, "bar blar {a TO z}");
    assertQueryEquals("gack ( bar blar { a TO z}) ", null, "gack (bar blar {a TO z})");

    //testSlop
    assertQueryEquals("\"term germ\"~2 flork", null, "\"term germ\"~2 flork");

    //testEscaped
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    assertQueryEquals("[\"c\\:\\\\temp\\\\\\~foo0.txt\" TO \"c\\:\\\\temp\\\\\\~foo9.txt\"]", a,
        "[c:\\temp\\~foo0.txt TO c:\\temp\\~foo9.txt]");
    assertQueryEquals("\"a \\\"b c\\\" d\"", a, "\"a \"b c\" d\"");
    assertQueryEquals("\"a \\+b c d\"", a, "\"a +b c d\"");
    assertQueryEquals("\"a \\\\\\u0028\\u0062\\\" c\"", a, "\"a \\(b\" c\"");

    // LUCENE-1189
    assertQueryEquals("(\"a\\\\\") or (\"b\")", a, "a\\ or b");

    //testQPA
    assertQueryEquals("term phrase term", qpAnalyzer,
        "term (phrase1 phrase2) term");
    assertQueryEquals("term AND NOT phrase term", qpAnalyzer,
        "+term -(phrase1 phrase2) term");

    CommonQueryParserConfiguration cqpc = getParserConfig(qpAnalyzer);
    setDefaultOperatorAND(cqpc);
    assertQueryEquals(cqpc, "field", "term phrase term",
        "+term +(+phrase1 +phrase2) +term");
    assertQueryEquals(cqpc, "field", "phrase",
        "+phrase1 +phrase2");
  }
}
