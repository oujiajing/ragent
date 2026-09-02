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

package com.nageoffer.ai.ragent.legal.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Opt-in developer runner. It is inert unless the Phase 2B import directory is explicitly configured. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.legal.phase2b", name = "import-enabled", havingValue = "true")
public class LegalCorpusImportRunner implements CommandLineRunner {

    private final LegalCorpusPersistenceService persistenceService;
    private final LegalCorpusIndexingService indexingService;

    @Override
    public void run(String... args) throws Exception {
        String configured = System.getProperty("rag.legal.phase2b.import-dir");
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Phase 2B import enabled but -Drag.legal.phase2b.import-dir is absent");
        }
        Path directory = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(directory)) throw new IllegalArgumentException("Legal corpus directory not found: " + directory);
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(this::importOne);
        }
        if (Boolean.parseBoolean(System.getProperty("rag.legal.phase2b.index-enabled", "false"))) {
            log.info("Phase 2B eligible chunk indexing started, eligible={}", indexingService.eligibleChunkCount());
            log.info("Phase 2B eligible chunk indexing completed, indexed={}", indexingService.indexAll());
        }
    }

    private void importOne(Path path) {
        try {
            LegalPersistenceResult result = persistenceService.importText(path.getFileName().toString(), Files.readAllBytes(path));
            log.info("Phase 2B legal import: file={}, status={}, documentId={}, elements={}, clauses={}, chunks={}",
                    path.getFileName(), result.status(), result.documentId(), result.elementCount(), result.clauseCount(), result.chunkCount());
        } catch (Exception e) {
            throw new IllegalStateException("Phase 2B legal import failed: " + path, e);
        }
    }
}
