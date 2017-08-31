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

package org.tallison.gramreaper.terms;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;

public class DumpFieldStats {

  public static void main(String[] args) throws Exception {
    Path indexPath = Paths.get(args[0]);
    IndexReader reader = DirectoryReader.open(FSDirectory.open(indexPath));
    LeafReader leafReader = SlowCompositeReaderWrapper.wrap(reader);
    Fields fields = leafReader.fields();
    for (String fieldName : fields) {
      System.out.println(fieldName + ":\n"+
          "\tDocCount: "+leafReader.getDocCount(fieldName) + "\n"+
          "\tUniqTerms: "+countUniqueTerms(fieldName, leafReader)+"\n"+
          "\tSumDocFreq: " + leafReader.getSumDocFreq(fieldName) + "\n"+
          "\tSumTotalTermFreq: "+leafReader.getSumTotalTermFreq(fieldName));
    }
  }

  private static long countUniqueTerms(String fieldName, LeafReader leafReader) throws IOException {
    Terms terms = leafReader.terms(fieldName);
    if (terms.size() > -1) {
      return terms.size();
    }
    TermsEnum termsEnum = terms.iterator();
    long count = 0;
    while (termsEnum.next() != null) {
      count++;
    }
    return count;
  }
}
