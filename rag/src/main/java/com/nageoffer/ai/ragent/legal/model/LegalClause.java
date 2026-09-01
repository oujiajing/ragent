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

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;

import java.util.List;

public record LegalClause(
        String clauseId,
        String documentId,
        LegalContentRole contentRole,
        LegalStructureType structureType,
        String chapterNo,
        String chapterTitle,
        String sectionNo,
        String sectionTitle,
        String clauseNo,
        String hierarchyPath,
        String rawText,
        String normalizedText,
        List<LegalSubUnit> children,
        String firstElementId,
        String lastElementId,
        Integer pageStart,
        Integer pageEnd,
        int sourceStartOffset,
        int sourceEndOffset
) {
    public LegalClause {
        if (clauseId == null || clauseId.isBlank()) {
            throw new IllegalArgumentException("clauseId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (contentRole == null || structureType == null) {
            throw new IllegalArgumentException("contentRole/structureType 不能为空");
        }
        if (clauseNo == null || clauseNo.isBlank()) {
            throw new IllegalArgumentException("clauseNo 不能为空");
        }
        if (sourceStartOffset < 0 || sourceEndOffset < sourceStartOffset) {
            throw new IllegalArgumentException("clause source provenance 非法");
        }
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        children = children == null ? List.of() : List.copyOf(children);
    }
}
