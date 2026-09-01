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

package com.nageoffer.ai.ragent.legal.model;

import com.nageoffer.ai.ragent.legal.enums.LegalQualityStatus;

import java.util.List;

public record LegalQualityReport(
        String documentId,
        Integer pageCount,
        int tableCount,
        int parsedTextLength,
        int chapterCount,
        int sectionCount,
        int clauseCount,
        int normativeClauseCount,
        int commentaryClauseCount,
        int supplementaryCount,
        int appendixCount,
        int unknownRoleCount,
        int unstructuredParagraphCount,
        int duplicateClauseCount,
        int chunkCount,
        int oversizedChunkCount,
        int emptyChunkCount,
        LegalQualityStatus qualityStatus,
        List<String> warnings
) {
    public LegalQualityReport {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
