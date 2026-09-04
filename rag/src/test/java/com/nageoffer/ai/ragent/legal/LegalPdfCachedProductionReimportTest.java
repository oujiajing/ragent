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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.TestRagentApplication;
import com.nageoffer.ai.ragent.legal.batch.LegalPdfBatchImportJob;
import com.nageoffer.ai.ragent.legal.batch.LegalPdfBatchImportResult;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusPersistenceService;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusIndexingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Explicit opt-in production replay for validated MinerU result.zip caches. */
@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "rag.image-parse.embedded-describe-enabled=false")
@EnabledIfSystemProperty(named = "legal.pdf.cached.persist", matches = "true")
class LegalPdfCachedProductionReimportTest {
    @Autowired LegalPdfBatchImportJob batchImportJob;
    @Autowired LegalCorpusPersistenceService persistenceService;
    @Autowired LegalCorpusIndexingService indexingService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void replacesTheApprovedCachedPdfCorpus() throws Exception {
        if (Boolean.parseBoolean(System.getProperty("legal.pdf.cached.index-only", "false"))) {
            int indexed = indexingService.indexEligiblePassPdfDocuments();
            System.out.println("PDF_INDEX_ONLY chunks=" + indexed);
            return;
        }
        Path manifest = Path.of(System.getProperty("legal.pdf.approved-manifest"));
        JsonNode root = objectMapper.readTree(Files.readAllBytes(manifest));
        Set<String> reviewHashes = new HashSet<>();
        for (JsonNode document : root.path("documents")) {
            if ("REVIEW_REQUIRED".equals(document.path("finalDisposition").asText())) {
                reviewHashes.add(document.path("fileHash").asText());
            }
        }
        Set<String> hashes = new HashSet<>();
        for (JsonNode document : root.path("documents")) hashes.add(document.path("fileHash").asText());
        persistenceService.deletePdfDocumentsByHashes(hashes.stream().toList());
        String sourceDirectory = System.getProperty("legal.pdf.source-dir");
        LegalPdfBatchImportResult result = batchImportJob.runCached(
                Path.of(System.getProperty("legal.pdf.cache-dir")),
                sourceDirectory == null || sourceDirectory.isBlank() ? null : Path.of(sourceDirectory), true,
                Boolean.parseBoolean(System.getProperty("legal.pdf.cached.index", "true")), reviewHashes);
        result.tasks().forEach(task -> System.out.println("PDF_CACHED_IMPORT " + task.fileName()
                + " status=" + task.status() + " clauses=" + task.clauseCount()
                + " chunks=" + task.chunkCount() + " error=" + task.errorMessage()));
        assertEquals(30, result.totalFiles());
    }
}
