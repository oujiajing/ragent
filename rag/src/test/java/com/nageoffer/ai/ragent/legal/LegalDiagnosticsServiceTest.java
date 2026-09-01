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

package com.nageoffer.ai.ragent.legal;

import com.nageoffer.ai.ragent.legal.diagnostics.LegalCorpusDiagnosticsService;
import com.nageoffer.ai.ragent.legal.diagnostics.LegalDuplicateType;
import com.nageoffer.ai.ragent.legal.diagnostics.UnstructuredDiagnosticType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalDiagnosticsServiceTest {

    @Test
    void shouldClassifyExactNearAndCrossRoleDuplicatesAndRemainDeterministic() throws Exception {
        String text = """
                1 总则
                表A 表格残留
                1.0.1 完全相同正文。
                1.0.1 完全相同正文。
                1.0.2 第一份正文。
                1.0.2 第二份正文含残留 标题。
                条文说明
                1.0.1 解释文本。
                """;
        Path source = Files.createTempFile("legal-diagnostics-", ".txt");
        try {
            Files.writeString(source, text, StandardCharsets.UTF_8);
            var diagnostics = new LegalCorpusDiagnosticsService(LegalTestFixtures.importer()).analyze(source);
            assertTrue(diagnostics.deterministic());
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.EXACT_DUPLICATE
                    && g.duplicateOrigin() == LegalDuplicateType.SOURCE_EXACT_DUPLICATE));
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.NEAR_DUPLICATE
                    && g.duplicateOrigin() == LegalDuplicateType.UNKNOWN));
            assertTrue(diagnostics.duplicateGroups().stream().anyMatch(g -> g.duplicateType() == LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE
                    && g.duplicateOrigin() == LegalDuplicateType.STRUCTURAL_VALID));
            assertEquals(1.0, diagnostics.sourceTextCoverageRatio());
            assertTrue(diagnostics.unstructuredItems().stream().anyMatch(item -> item.diagnosticType() == UnstructuredDiagnosticType.TABLE_RESIDUE));
        } finally {
            Files.deleteIfExists(source);
        }
    }
}
