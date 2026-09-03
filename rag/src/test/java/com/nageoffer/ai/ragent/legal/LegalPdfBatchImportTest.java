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

import com.nageoffer.ai.ragent.legal.batch.LegalPdfBatchImportJob;
import com.nageoffer.ai.ragent.legal.batch.LegalPdfImportTaskStatus;
import com.nageoffer.ai.ragent.legal.ingest.LegalPdfImportService;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusIndexingService;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusPersistenceService;
import com.nageoffer.ai.ragent.legal.persistence.LegalPersistenceResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LegalPdfBatchImportTest {

    @Test
    void oneFailureDoesNotAbortOtherFiles() throws Exception {
        Path dir = tempFiles("a.pdf", "b.pdf");
        LegalPdfImportService parser = mock(LegalPdfImportService.class);
        CleanedTextImportResult success = LegalTestFixtures.importer().importText(
                "doc", "a.txt", "第一条 文本。".getBytes(), com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode.DRY_RUN);
        when(parser.dryRun(anyString(), anyString(), any())).thenAnswer(invocation ->
                invocation.getArgument(1, String.class).equals("a.pdf") ? success : fail("network error"));

        var result = new LegalPdfBatchImportJob(parser, mock(LegalCorpusPersistenceService.class),
                mock(LegalCorpusIndexingService.class)).run(dir, false, false);

        assertEquals(2, result.totalFiles());
        assertEquals(1, result.successCount());
        assertEquals(1, result.failedCount());
        assertEquals(LegalPdfImportTaskStatus.STRUCTURED, result.tasks().get(0).status());
        assertEquals(20, result.tasks().get(0).id().length());
        assertEquals(LegalPdfImportTaskStatus.FAILED, result.tasks().get(1).status());
    }

    @Test
    void retriesTimeoutAtMostThreeAttempts() throws Exception {
        Path dir = tempFiles("a.pdf");
        LegalPdfImportService parser = mock(LegalPdfImportService.class);
        CleanedTextImportResult success = LegalTestFixtures.importer().importText(
                "doc", "a.txt", "第一条 文本。".getBytes(), com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode.DRY_RUN);
        AtomicInteger calls = new AtomicInteger();
        when(parser.dryRun(anyString(), anyString(), any())).thenAnswer(invocation -> {
            if (calls.incrementAndGet() < 3) throw fail("upload timeout");
            return success;
        });

        var result = new LegalPdfBatchImportJob(parser, mock(LegalCorpusPersistenceService.class),
                mock(LegalCorpusIndexingService.class)).run(dir, false, false);

        assertEquals(3, calls.get());
        assertEquals(2, result.tasks().get(0).retryCount());
        assertEquals(LegalPdfImportTaskStatus.STRUCTURED, result.tasks().get(0).status());
    }

    @Test
    void skipsMineruWhenOriginalPdfWasAlreadyImported() throws Exception {
        Path dir = tempFiles("a.pdf");
        LegalPdfImportService parser = mock(LegalPdfImportService.class);
        LegalCorpusPersistenceService persistence = mock(LegalCorpusPersistenceService.class);
        LegalCorpusIndexingService indexing = mock(LegalCorpusIndexingService.class);
        byte[] bytes = Files.readAllBytes(dir.resolve("a.pdf"));
        doReturn("existing").when(persistence).findImported(anyString(), anyString());

        var result = new LegalPdfBatchImportJob(parser, persistence, indexing).run(dir, true, true);

        verifyNoInteractions(parser);
        verify(indexing).indexAll();
        assertEquals(LegalPdfImportTaskStatus.INDEXED, result.tasks().get(0).status());
        assertEquals(0, result.tasks().get(0).chunkCount());
        assertTrue(bytes.length > 0);
    }

    private Path tempFiles(String... names) throws Exception {
        Path dir = Files.createTempDirectory("legal-pdf-batch-");
        for (String name : names) Files.writeString(dir.resolve(name), "%PDF synthetic");
        return dir;
    }

    private RuntimeException fail(String message) {
        return new RuntimeException(message);
    }
}
