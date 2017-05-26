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
 *
 * NOTICE

 * This software was produced for the U.S. Government
 * under Basic Contract No. W15P7T-13-C-A802,
 * W15P7T-12-C-F600, and W15P7T-13-C-F600, and is
 * subject to the Rights in Noncommercial Computer Software
 * and Noncommercial Computer Software Documentation
 * Clause 252.227-7014 (FEB 2012)
 *
 * (C) 2013-2016 The MITRE Corporation. All Rights Reserved.
 *
 * NOTICE
 * This (software/technical data) was produced for the U.S. Government under
 * Contract Number TIRNO-99-D00005, and is subject to Federal Acquisition
 * Regulation Clause 52.227-14, Rights in Data--General, Alt. II, III and IV (DEC
 * 2007) [Reference 27.409(a)].
 *
 * No other use than that granted to the U.S. Government, or to those acting
 * on behalf of the U.S. Government under that Clause is authorized without the
 * express written permission of The MITRE Corporation.
 *
 * To the extent necessary MITRE hereby grants express written permission to use,
 * reproduce, distribute, modify, and otherwise leverage this software to the extent
 * permitted by the Apache 2.0 license.
 *
 * For further information, please contact The MITRE Corporation, Contracts
 * Office, 7515 Colshire Drive, McLean, VA 22102-7539, (703) 983-6000.
 *
 * (C) 2013-2016 The MITRE Corporation. All Rights Reserved.
 */
package org.tallison.gramreaper.ingest.schema;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.core.KeywordTokenizerFactory;
import org.apache.lucene.analysis.util.AbstractAnalysisFactory;
import org.apache.lucene.analysis.util.CharFilterFactory;
import org.apache.lucene.analysis.util.MultiTermAwareComponent;
import org.apache.lucene.analysis.util.TokenFilterFactory;
import org.apache.lucene.analysis.util.TokenizerFactory;

/**
 * Important components stolen directly from Solr
 */
abstract class AnalyzingFieldDefBase {

    final static String INDEX_ANALYZER = "index_analyzer";
    final static String QUERY_ANALYZER = "query_analyzer";
    final static String MTQUERY_ANALYZER = "mt_query_analyzer";
    final static String OFFSET_ANALYZER = "offset_analyzer";

    private Analyzer indexAnalyzer;
    private Analyzer queryAnalyzer;
    private Analyzer mtQueryAnalyzer;
    private Analyzer offsetAnalyzer;

    private String indexAnalyzerName;
    private String queryAnalyzerName;
    private String mtQueryAnalyzerName;
    private String offsetAnalyzerName;

    public AnalyzingFieldDefBase(){}

    public void setAnalyzers(NamedAnalyzer namedIndexAnalyzer,
                                NamedAnalyzer namedQueryAnalyzer,
                                NamedAnalyzer namedMTQueryAnalyzer,
                                NamedAnalyzer namedOffsetAnalyzer){
        this.indexAnalyzer = (namedIndexAnalyzer != null) ? namedIndexAnalyzer.analyzer : null;
        this.queryAnalyzer = (namedQueryAnalyzer != null) ? namedQueryAnalyzer.analyzer : this.indexAnalyzer;
        this.mtQueryAnalyzer = (namedMTQueryAnalyzer != null) ? namedMTQueryAnalyzer.analyzer : constructMultiTermAnalyzer(this.queryAnalyzer);
        this.offsetAnalyzer = (namedOffsetAnalyzer != null) ? namedOffsetAnalyzer.analyzer : this.indexAnalyzer;
        indexAnalyzerName = (namedIndexAnalyzer != null) ? namedIndexAnalyzer.name : null;
        queryAnalyzerName = (namedQueryAnalyzer != null) ? namedQueryAnalyzer.name : null;
        mtQueryAnalyzerName = (namedMTQueryAnalyzer != null) ? namedMTQueryAnalyzer.name : null;
        offsetAnalyzerName = (namedOffsetAnalyzer != null) ? namedOffsetAnalyzer.name : null;
    }

    public Analyzer getIndexAnalyzer() {
        return indexAnalyzer;
    }

    public Analyzer getQueryAnalyzer() {
        return queryAnalyzer;
    }

    public Analyzer getMultitermQueryAnalyzer() {
        return mtQueryAnalyzer;
    }

    public Analyzer getOffsetAnalyzer() {
        return offsetAnalyzer;
    }

    public String getIndexAnalyzerName() {
        return indexAnalyzerName;
    }

    public String getQueryAnalyzerName() {
        return queryAnalyzerName;
    }

    public String getMtQueryAnalyzerName() {
        return mtQueryAnalyzerName;
    }

    public String getOffsetAnalyzerName() {
        return offsetAnalyzerName;
    }

    private Analyzer constructMultiTermAnalyzer(Analyzer queryAnalyzer) {
        if (queryAnalyzer == null) return null;

        if (!(queryAnalyzer instanceof MyTokenizerChain)) {
            return new KeywordAnalyzer();
        }

        MyTokenizerChain tc = (MyTokenizerChain)queryAnalyzer;
        MultiTermChainBuilder builder = new MultiTermChainBuilder();

        CharFilterFactory[] charFactories = tc.getCharFilterFactories();
        if (charFactories != null) {
            for (CharFilterFactory fact : charFactories) {
                builder.add(fact);
            }
        }

        builder.add(tc.getTokenizerFactory());

        for (TokenFilterFactory fact : tc.getTokenFilterFactories()) {
            builder.add(fact);
        }

        return builder.build();
    }

    private static class MultiTermChainBuilder {
        static final KeywordTokenizerFactory keyFactory = new KeywordTokenizerFactory(new HashMap<String, String>());

        ArrayList<CharFilterFactory> charFilters = null;
        ArrayList<TokenFilterFactory> filters = new ArrayList<>(2);
        TokenizerFactory tokenizer = keyFactory;

        public void add(Object current) {
            if (!(current instanceof MultiTermAwareComponent)) return;
            AbstractAnalysisFactory newComponent = ((MultiTermAwareComponent) current).getMultiTermComponent();
            if (newComponent instanceof TokenFilterFactory) {
                if (filters == null) {
                    filters = new ArrayList<>(2);
                }
                filters.add((TokenFilterFactory) newComponent);
            } else if (newComponent instanceof TokenizerFactory) {
                tokenizer = (TokenizerFactory) newComponent;
            } else if (newComponent instanceof CharFilterFactory) {
                if (charFilters == null) {
                    charFilters = new ArrayList<>(1);
                }
                charFilters.add((CharFilterFactory) newComponent);

            } else {
                throw new IllegalArgumentException("Unknown analysis component from MultiTermAwareComponent: " + newComponent);
            }
        }

        public MyTokenizerChain build() {
            CharFilterFactory[] charFilterArr = charFilters == null ? null : charFilters.toArray(new CharFilterFactory[charFilters.size()]);
            TokenFilterFactory[] filterArr = filters == null ? new TokenFilterFactory[0] : filters.toArray(new TokenFilterFactory[filters.size()]);
            return new MyTokenizerChain(charFilterArr, tokenizer, filterArr);
        }
    }

}
