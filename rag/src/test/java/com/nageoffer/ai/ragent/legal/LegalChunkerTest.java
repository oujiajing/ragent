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

import com.nageoffer.ai.ragent.legal.config.LegalIngestionProperties;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalChunkerTest {

    @Test
    void shouldSplitLongClauseAtItemsAndSentencesWithoutSubstringCut() {
        LegalIngestionProperties properties = LegalTestFixtures.properties();
        properties.getChunk().setMaxTokens(45);
        properties.getChunk().setHardLimitTokens(100);
        String text = """
                建筑施工安全检查标准
                JGJ 59-2011
                3 检查评定项目
                3.1 安全管理
                3.1.1 安全管理检查评定应符合下列规定：
                1. 工程项目部应建立安全生产责任制，并由责任人签字确认。
                2. 工程项目部应编制施工组织设计。专项施工方案应按规定审核批准。
                3. 工程项目部应定期组织安全检查，对发现的隐患及时整改。
                """;

        CleanedTextImportResult result = LegalTestFixtures.importer(properties).importText(
                "2000000000000000002", "《建筑施工安全检查标准》JGJ 59-2011.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);

        assertTrue(result.chunks().size() >= 2);
        assertTrue(result.chunks().stream().allMatch(c -> c.metadata().parentClauseId()
                .equals(result.document().clauses().get(0).clauseId())));
        assertTrue(result.chunks().stream().allMatch(c -> c.content().contains("3.1.1")));
        assertTrue(result.chunks().stream().noneMatch(c -> c.sourceText().isBlank()));
        assertEquals(0, result.qualityReport().emptyChunkCount());
        assertEquals(0, result.qualityReport().oversizedChunkCount());
    }

    @Test
    void shouldKeepNormalClauseAsOneChunk() {
        String text = """
                建筑施工安全检查标准
                JGJ 59-2011
                1 总则
                1.0.1 为规范建筑施工安全检查，制定本标准。
                """;
        CleanedTextImportResult result = LegalTestFixtures.importer().importText(
                "2000000000000000003", "《建筑施工安全检查标准》JGJ 59-2011.txt",
                text.getBytes(StandardCharsets.UTF_8), CleanedTextImportMode.DRY_RUN);
        assertEquals(1, result.chunks().size());
        assertEquals("1.0.1", result.chunks().get(0).metadata().clauseNo());
    }
}
