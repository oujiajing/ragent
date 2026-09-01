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

public record CleanedTextImportResult(
        NormalizedLegalDocument document,
        List<LegalChunk> chunks,
        LegalQualityReport qualityReport,
        String canonicalSourceText
) {
    public CleanedTextImportResult {
        if (document == null || qualityReport == null) throw new IllegalArgumentException("dry-run 结果不能为空");
        chunks = chunks == null ? List.of() : List.copyOf(chunks);
        canonicalSourceText = canonicalSourceText == null ? "" : canonicalSourceText;
    }
}
