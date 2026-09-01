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

import com.nageoffer.ai.ragent.legal.enums.LegalQualityStatus;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalQualityServiceTest {

    @Test
    void shouldFailDocumentWithoutClause() {
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000004", "无条款文件.txt",
                "只有一段无法识别的正文。".getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalQualityStatus.FAILED, result.qualityReport().qualityStatus());
        assertEquals(0, result.qualityReport().clauseCount());
    }

    @Test
    void shouldReviewDuplicateClauseWithinSameRole() {
        String text = """
                建筑施工安全检查标准
                1 总则
                1.0.1 第一份正文。
                1.0.1 第二份同号正文。
                """;
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000005", "普通标准.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(LegalQualityStatus.REVIEW, result.qualityReport().qualityStatus());
        assertEquals(1, result.qualityReport().duplicateClauseCount());
        assertTrue(result.qualityReport().warnings().stream().anyMatch(w -> w.contains("重复条款")));
    }
}
