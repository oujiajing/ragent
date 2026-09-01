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

public record LegalDocumentElement(
        String elementId,
        String documentId,
        int elementIndex,
        String rawText,
        String normalizedText,
        LegalStructureType structureType,
        LegalContentRole contentRole,
        String canonicalNumber,
        Integer pageStart,
        Integer pageEnd
) {
    public LegalDocumentElement {
        if (elementId == null || elementId.isBlank()) {
            throw new IllegalArgumentException("elementId 不能为空");
        }
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        if (elementIndex < 0) {
            throw new IllegalArgumentException("elementIndex 必须 >= 0");
        }
        rawText = rawText == null ? "" : rawText;
        normalizedText = normalizedText == null ? "" : normalizedText;
        structureType = structureType == null ? LegalStructureType.UNKNOWN : structureType;
        contentRole = contentRole == null ? LegalContentRole.UNKNOWN : contentRole;
    }

    public LegalDocumentElement classified(LegalStructureType type, LegalContentRole role, String number) {
        return new LegalDocumentElement(elementId, documentId, elementIndex, rawText, normalizedText,
                type, role, number, pageStart, pageEnd);
    }
}
