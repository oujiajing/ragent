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

package com.nageoffer.ai.ragent.legal.clean;

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentElement;
import com.nageoffer.ai.ragent.legal.model.LegalSourceBlock;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class LegalCleaningPipeline {

    private final List<LegalCleaningStep> steps;

    public LegalCleaningPipeline(List<LegalCleaningStep> steps) {
        this.steps = steps.stream().sorted(Comparator.comparingInt(LegalCleaningStep::order)).toList();
    }

    public List<LegalDocumentElement> clean(String documentId, String rawText) {
        if (rawText == null || rawText.isEmpty()) return List.of();
        String[] lines = rawText.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        java.util.ArrayList<LegalDocumentElement> result = new java.util.ArrayList<>();
        for (int sourceLine = 0; sourceLine < lines.length; sourceLine++) {
            String normalized = lines[sourceLine];
            for (LegalCleaningStep step : steps) normalized = step.normalize(normalized);
            if (normalized.isBlank()) continue;
            int startOffset = result.isEmpty() ? 0 : result.get(result.size() - 1).sourceEndOffset() + 1;
            int endOffset = startOffset + normalized.length();
            result.add(new LegalDocumentElement(
                    documentId + ":e:" + sourceLine,
                    documentId,
                    result.size(),
                    lines[sourceLine],
                    normalized,
                    LegalStructureType.UNKNOWN,
                    LegalContentRole.UNKNOWN,
                    null,
                    null,
                    null,
                    sourceLine,
                    startOffset,
                    endOffset));
        }
        return List.copyOf(result);
    }

    /** One PDF Block becomes one element. Soft line breaks cannot become new clause boundaries. */
    public List<LegalDocumentElement> cleanBlocks(String documentId, List<LegalSourceBlock> blocks) {
        java.util.ArrayList<LegalDocumentElement> result = new java.util.ArrayList<>();
        int sourceLine = 0;
        int offset = 0;
        for (int i = 0; i < blocks.size(); i++) {
            LegalSourceBlock block = blocks.get(i);
            String raw = block.text() == null ? "" : block.text();
            String normalized = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ');
            for (LegalCleaningStep step : steps) normalized = step.normalize(normalized);
            if (!normalized.isBlank()) {
                LegalStructureType type = switch (block.kind()) {
                    case HEADING -> LegalStructureType.SOURCE_HEADING;
                    case TABLE -> LegalStructureType.TABLE;
                    case LIST -> LegalStructureType.ITEM;
                    default -> LegalStructureType.PARAGRAPH;
                };
                result.add(new LegalDocumentElement(documentId + ":b:" + i, documentId, result.size(), raw,
                        normalized, type, block.body() ? LegalContentRole.NORMATIVE : LegalContentRole.UNKNOWN,
                        null, null, null, sourceLine, offset, offset + normalized.length()));
                offset += normalized.length() + 1;
            }
            sourceLine += (int) raw.chars().filter(c -> c == '\n').count() + 1;
        }
        return List.copyOf(result);
    }
}
