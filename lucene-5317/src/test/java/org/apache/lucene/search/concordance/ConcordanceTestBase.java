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
package org.apache.lucene.search.concordance;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.MockTokenFilter;
import org.apache.lucene.analysis.MockTokenizer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.asserting.AssertingCodec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.FieldInfo.IndexOptions;
import org.apache.lucene.search.concordance.charoffsets.SimpleAnalyzerUtil;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.TestUtil;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;

public class ConcordanceTestBase extends LuceneTestCase {

  protected final static String FIELD = "f1";

  public Directory getDirectory(Analyzer analyzer, String[] vals)
      throws IOException {
    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(random(), TEST_VERSION_CURRENT, analyzer)
            .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
            .setMergePolicy(newLogMergePolicy()));

    for (String s : vals) {
      Document d = new Document();
      d.add(newTextField(FIELD, s, Field.Store.YES));
      writer.addDocument(d);
    }
    writer.close();
    return directory;
  }

  public Directory getDirectory(Analyzer analyzer, List<String[]> input)
      throws IOException {

    Directory directory = newDirectory();
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory,
        newIndexWriterConfig(random(), TEST_VERSION_CURRENT, analyzer)
            .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
            .setMergePolicy(newLogMergePolicy()));
    
    for (String[] vals : input) {
      Document d = new Document();
      for (String s : vals) {
        d.add(newTextField(FIELD, s, Field.Store.YES));
      }
      writer.addDocument(d);
    }
    writer.close();
    return directory;
  }

  public static Analyzer getAnalyzer(final CharacterRunAutomaton stops) {
    return getAnalyzer(stops, random().nextInt(10000), random().nextInt(10000));
  }
  
  public static Analyzer getAnalyzer(final CharacterRunAutomaton stops, 
    final int posIncGap, final int charOffsetGap) {
    
    Analyzer analyzer = new Analyzer() {

      @Override
      public TokenStreamComponents createComponents(String fieldName, Reader reader) {
        Tokenizer tokenizer = new MockTokenizer(reader, MockTokenizer.WHITESPACE, true);
        TokenFilter filter = new MockTokenFilter(tokenizer, stops);
        return new TokenStreamComponents(tokenizer, filter);
      }

      @Override
      public int getPositionIncrementGap(String fieldName) {
        return posIncGap;
      }
      
      @Override
      public int getOffsetGap(String fieldName) {
        return charOffsetGap;
      }
    };
    return analyzer;
  }
  
  protected Directory buildNeedleIndex(String needle, 
      Analyzer analyzer, int numFieldValues) throws Exception {
    
    IndexWriterConfig config = newIndexWriterConfig(random(), TEST_VERSION_CURRENT, analyzer)
        .setMaxBufferedDocs(TestUtil.nextInt(random(), 100, 1000))
        .setMergePolicy(newLogMergePolicy());
    
    Directory directory = newDirectory();
    String pf = TestUtil.getPostingsFormat(FIELD);
    if (doesntSupportOffsets.contains(pf)) {
      //just use Asserting
      Codec codec = new AssertingCodec();
      config.setCodec(codec);
    }
    RandomIndexWriter writer = new RandomIndexWriter(random(), directory, config);
    //create document with multivalued field
    String[] fs = new String[numFieldValues];
    for (int i = 0; i < numFieldValues; i++) {
      float r = random().nextFloat();
      String doc = "";
      if (r <= 0.33) {
        doc = needle+" "+getRandomWords(29, needle, analyzer);
      } else if (r <= 0.66) {
        doc = getRandomWords(13, needle, analyzer)+" "+needle+" "+getRandomWords(17, needle, analyzer);
      } else {
        doc = getRandomWords(31, needle, analyzer)+" "+needle;
      }
      fs[i] = doc;
    }

    Document d = new Document();
    FieldType type = new FieldType();
    type.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    type.setStored(true);
    type.setIndexed(true);

    for (String s : fs) {
      d.add(newField(FIELD, s, type));
    }
    writer.addDocument(d);   
    writer.close();
    return directory;
  }
  
  
  
  /**
   * this assumes no stop filter in the analyzer.
   * Best to use whitespace tokenizer.
   */
  private String getRandomWords(int numWords, String needle, Analyzer analyzer) throws Exception {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < numWords; i++ ) {
      sb.append(TestUtil.randomUnicodeString(random(), 31));
      sb.append(" ");
    }
    List<String> terms = SimpleAnalyzerUtil.getTermStrings(sb.toString(), analyzer);
    StringBuilder rsb = new StringBuilder();
    int words = -1;
    while (words++ < numWords && words < terms.size()) {
      String cand = terms.get(words);
      if (!needle.equals(cand)) {
        if (words > 0) {
          rsb.append(" ");
        }
        rsb.append(cand);
      }
    }
    return rsb.toString();
  }


  protected String getNeedle(Analyzer analyzer){
    //try to get a term that would come out of the analyzer
    for (int i = 0; i < 10; i++) {
      //start with a random base string
      String baseString = TestUtil.randomUnicodeString(random(), random().nextInt(10)+2);
      
      try{
        //run it through the analyzer, and take the first thing
        //that comes out of it if the length > 0
        List<String> terms = SimpleAnalyzerUtil.getTermStrings(baseString, analyzer);
        for (String t : terms){
          if (t.length() > 0){
            return t;
          }
        }
      } catch (IOException e){
        //swallow
      }
    }
    //if nothing is found in 10 tries,
    //return literal string "needle"
    
    return "needle";
  }
}