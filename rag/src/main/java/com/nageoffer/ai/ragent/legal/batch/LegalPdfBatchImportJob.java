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

package com.nageoffer.ai.ragent.legal.batch;

import com.nageoffer.ai.ragent.legal.ingest.LegalPdfImportService;
import com.nageoffer.ai.ragent.legal.ingest.LegalDocumentImportAdapter;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusIndexingService;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusPersistenceService;
import com.nageoffer.ai.ragent.legal.persistence.LegalPersistenceResult;
import com.nageoffer.ai.ragent.legal.util.LegalHashes;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Batch coordinator with bounded retries; one failed PDF never aborts the remaining files. */
@Service
public class LegalPdfBatchImportJob {
    private static final int MAX_ATTEMPTS = 3;

    private final LegalPdfImportService pdfImportService;
    private final LegalCorpusPersistenceService persistenceService;
    private final LegalCorpusIndexingService indexingService;

    public LegalPdfBatchImportJob(LegalPdfImportService pdfImportService,
                                  LegalCorpusPersistenceService persistenceService,
                                  LegalCorpusIndexingService indexingService) {
        this.pdfImportService = pdfImportService;
        this.persistenceService = persistenceService;
        this.indexingService = indexingService;
    }

    public LegalPdfBatchImportResult run(Path directory, boolean persist, boolean index) throws Exception {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalArgumentException("PDF directory 不存在: " + directory);
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        List<LegalPdfImportTask> tasks = new ArrayList<>();
        for (Path file : files) tasks.add(runOne(file, persist));
        if (persist && index) {
            indexingService.indexAll();
            tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED).forEach(LegalPdfImportTask::indexed);
        }
        int success = (int) tasks.stream().filter(t -> t.status() == LegalPdfImportTaskStatus.STRUCTURED
                || t.status() == LegalPdfImportTaskStatus.INDEXED).count();
        return new LegalPdfBatchImportResult(tasks, tasks.size(), success, tasks.size() - success,
                tasks.stream().mapToInt(LegalPdfImportTask::retryCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::clauseCount).sum(),
                tasks.stream().mapToInt(LegalPdfImportTask::chunkCount).sum());
    }

    private LegalPdfImportTask runOne(Path file, boolean persist) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        String fileName = file.getFileName().toString();
        String hash = LegalHashes.sha256(bytes);
        LegalPdfImportTask task = new LegalPdfImportTask("pdf-task-" + hash.substring(0, 16), fileName, hash);
        if (persist && persistenceService.findImported(hash, LegalDocumentImportAdapter.PARSER_VERSION) != null) {
            task.structured(0, 0);
            return task;
        }
        Exception failure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                task.parsing();
                CleanedTextImportResult result = pdfImportService.dryRun(task.id(), fileName, bytes);
                task.structured(result.document().clauses().size(), result.chunks().size());
                if (persist) {
                    LegalPersistenceResult persisted = persistenceService.importPdf(fileName, bytes, result);
                    if ("ALREADY_IMPORTED".equals(persisted.status())) {
                        task.structured(persisted.clauseCount(), persisted.chunkCount());
                    }
                }
                return task;
            } catch (Exception error) {
                failure = error;
                if (!isRetryable(error) || attempt == MAX_ATTEMPTS) break;
                task.retrying();
            }
        }
        task.failed(failure == null ? new IllegalStateException("PDF import failed") : failure);
        return task;
    }

    private boolean isRetryable(Exception error) {
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
        return message.contains("timeout") || message.contains("network") || message.contains("connection");
    }
}
