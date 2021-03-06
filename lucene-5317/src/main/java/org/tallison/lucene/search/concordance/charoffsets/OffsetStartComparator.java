package org.tallison.lucene.search.concordance.charoffsets;

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

import java.io.Serializable;
import java.util.Comparator;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * sort on offset start
 */
public class OffsetStartComparator implements Comparator<OffsetAttribute>,
    Serializable {
  private static final long serialVersionUID = 7526472295622776147L;

  @Override
  public int compare(OffsetAttribute offsetA, OffsetAttribute offsetB) {

    if (offsetA.startOffset() < offsetB.startOffset()) {
      return -1;
    } else if (offsetA.startOffset() > offsetB.startOffset()) {
      return 1;
    }
    return 0;
  }

}
