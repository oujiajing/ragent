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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusPersistenceService;
import com.nageoffer.ai.ragent.legal.persistence.LegalPersistenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegalCorpusPersistenceIdempotencyTest {

    @Test
    void returnsAlreadyImportedForTheSameOriginalBytesAndParserVersion() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of("existing-document"));

        LegalCorpusPersistenceService service = new LegalCorpusPersistenceService(
                jdbc, new ObjectMapper(), LegalTestFixtures.importer());
        LegalPersistenceResult result = service.importText(
                "sample.pdf", "第一条 文本。".getBytes(StandardCharsets.UTF_8));

        assertEquals("ALREADY_IMPORTED", result.status());
        assertEquals("existing-document", result.documentId());
        assertEquals(0, result.chunkCount());
        org.mockito.Mockito.verify(jdbc).query(anyString(), any(RowMapper.class), any(), any(), any());
    }
}
