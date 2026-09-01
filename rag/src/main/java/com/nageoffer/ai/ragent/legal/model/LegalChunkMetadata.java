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

import com.nageoffer.ai.ragent.legal.enums.LegalChunkType;
import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;

import java.util.LinkedHashMap;
import java.util.Map;

public record LegalChunkMetadata(
        String documentId,
        String docTitle,
        String standardNo,
        String chapterNo,
        String chapterTitle,
        String sectionNo,
        String sectionTitle,
        String clauseNo,
        String hierarchyPath,
        String parentClauseId,
        String childRange,
        LegalContentRole contentRole,
        LegalChunkType chunkType,
        Integer pageStart,
        Integer pageEnd
) {
    public Map<String, Object> toMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "document_id", documentId);
        put(result, "doc_title", docTitle);
        put(result, "standard_no", standardNo);
        put(result, "chapter_no", chapterNo);
        put(result, "chapter_title", chapterTitle);
        put(result, "section_no", sectionNo);
        put(result, "section_title", sectionTitle);
        put(result, "clause_no", clauseNo);
        put(result, "hierarchy_path", hierarchyPath);
        put(result, "parent_clause_id", parentClauseId);
        put(result, "child_range", childRange);
        if (contentRole != null) result.put("content_role", contentRole.name());
        if (chunkType != null) result.put("chunk_type", chunkType.name());
        if (pageStart != null) result.put("page_start", pageStart);
        if (pageEnd != null) result.put("page_end", pageEnd);
        return Map.copyOf(result);
    }

    private static void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }
}
