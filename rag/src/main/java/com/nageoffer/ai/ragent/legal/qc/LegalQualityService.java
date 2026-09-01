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

package com.nageoffer.ai.ragent.legal.qc;

import com.nageoffer.ai.ragent.legal.config.LegalIngestionProperties;
import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.enums.LegalQualityStatus;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;
import com.nageoffer.ai.ragent.legal.model.LegalChunk;
import com.nageoffer.ai.ragent.legal.model.LegalClause;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentElement;
import com.nageoffer.ai.ragent.legal.model.LegalQualityReport;
import com.nageoffer.ai.ragent.legal.model.NormalizedLegalDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LegalQualityService {

    private final LegalIngestionProperties properties;

    public LegalQualityService(LegalIngestionProperties properties) {
        this.properties = properties;
    }

    public LegalQualityReport assess(NormalizedLegalDocument document, List<LegalChunk> chunks) {
        List<String> warnings = new ArrayList<>(document.warnings());
        int parsedTextLength = document.elements().stream().mapToInt(e -> e.normalizedText().length()).sum();
        int chapterCount = countType(document.elements(), LegalStructureType.CHAPTER);
        int sectionCount = countType(document.elements(), LegalStructureType.SECTION);
        int normative = countClauses(document.clauses(), LegalContentRole.NORMATIVE);
        int commentary = countClauses(document.clauses(), LegalContentRole.COMMENTARY);
        int supplementary = countRole(document.elements(), LegalContentRole.SUPPLEMENTARY);
        int appendix = countRole(document.elements(), LegalContentRole.APPENDIX);
        int unknown = countRole(document.elements(), LegalContentRole.UNKNOWN);
        int duplicates = duplicateClauseCount(document.clauses());
        int empty = (int) chunks.stream().filter(c -> c.content().isBlank()).count();
        int oversized = (int) chunks.stream()
                .filter(c -> c.tokenCount() > properties.getChunk().getHardLimitTokens()).count();
        int unstructured = document.unstructuredParagraphs().size();

        boolean failed = parsedTextLength == 0 || document.clauses().isEmpty() || chunks.isEmpty() || empty > 0;
        boolean review = false;
        if (oversized > 0) {
            review = true;
            warnings.add("存在 " + oversized + " 个超过 hardLimitTokens 的 chunk");
        }
        if (duplicates > 0) {
            review = true;
            warnings.add("存在 " + duplicates + " 个同 (contentRole, clauseNo) 重复条款");
        }
        double unstructuredRatio = ratio(unstructured, document.elements().size());
        if (unstructuredRatio > properties.getQuality().getMaxUnstructuredRatio()) {
            review = true;
            warnings.add(String.format("未结构化段落比例 %.2f%% 超过阈值", unstructuredRatio * 100));
        }
        double unknownRatio = ratio(unknown, document.elements().size());
        if (unknownRatio > properties.getQuality().getMaxUnknownRoleRatio()) {
            review = true;
            warnings.add(String.format("UNKNOWN contentRole 比例 %.2f%% 超过阈值", unknownRatio * 100));
        }
        if (warnings.stream().anyMatch(w -> w.contains("冲突"))) review = true;

        LegalQualityStatus status = failed ? LegalQualityStatus.FAILED
                : review ? LegalQualityStatus.REVIEW : LegalQualityStatus.PASS;
        return new LegalQualityReport(
                document.metadata().documentId(), null, 0,
                parsedTextLength, chapterCount, sectionCount, document.clauses().size(),
                normative, commentary, supplementary, appendix, unknown, unstructured,
                duplicates, chunks.size(), oversized, empty, status, warnings);
    }

    private int duplicateClauseCount(List<LegalClause> clauses) {
        Map<String, Integer> counts = new HashMap<>();
        for (LegalClause clause : clauses) {
            counts.merge(clause.contentRole() + "|" + clause.clauseNo(), 1, Integer::sum);
        }
        return counts.values().stream().mapToInt(count -> Math.max(0, count - 1)).sum();
    }

    private int countType(List<LegalDocumentElement> elements, LegalStructureType type) {
        return (int) elements.stream().filter(e -> e.structureType() == type).count();
    }

    private int countRole(List<LegalDocumentElement> elements, LegalContentRole role) {
        return (int) elements.stream().filter(e -> e.contentRole() == role).count();
    }

    private int countClauses(List<LegalClause> clauses, LegalContentRole role) {
        return (int) clauses.stream().filter(c -> c.contentRole() == role).count();
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0 : (double) numerator / denominator;
    }
}
