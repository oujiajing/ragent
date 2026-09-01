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

import java.util.List;

public record NormalizedLegalDocument(
        LegalDocumentMetadata metadata,
        List<LegalDocumentElement> elements,
        List<LegalClause> clauses,
        List<LegalDocumentElement> unstructuredParagraphs,
        List<String> warnings
) {
    public NormalizedLegalDocument {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata 不能为空");
        }
        elements = elements == null ? List.of() : List.copyOf(elements);
        clauses = clauses == null ? List.of() : List.copyOf(clauses);
        unstructuredParagraphs = unstructuredParagraphs == null ? List.of() : List.copyOf(unstructuredParagraphs);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
