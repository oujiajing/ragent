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

import com.nageoffer.ai.ragent.TestRagentApplication;
import com.nageoffer.ai.ragent.legal.ingest.LegalPdfImportService;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = TestRagentApplication.class, webEnvironment = WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "legal.pdf.dir", matches = ".+")
class LegalPdfCorpusDryRunTest {

    @Autowired
    private LegalPdfImportService importService;

    @Test
    void dryRunsTenPdfDocumentsThroughMineruAndLegalParser() throws Exception {
        Path corpus = Path.of(System.getProperty("legal.pdf.dir")).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(corpus), "PDF corpus 不存在: " + corpus);
        List<Path> pdfs;
        try (var files = Files.list(corpus)) {
            pdfs = files.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".pdf"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        }
        assertEquals(10, pdfs.size(), "MVP 应包含 10 份法规 PDF");

        StringBuilder report = new StringBuilder("# Phase 5.1 PDF Dry Run\n\n")
                .append("| file | clauses | chunks | clause_no | hierarchy | quality |\n")
                .append("|---|---:|---:|---:|---:|---|\n");
        int succeeded = 0;
        for (int i = 0; i < pdfs.size(); i++) {
            Path pdf = pdfs.get(i);
            byte[] bytes = Files.readAllBytes(pdf);
            String documentId = "pdfdry" + i;
            try {
                CleanedTextImportResult result = importService.dryRun(documentId,
                        pdf.getFileName().toString(), bytes);
                var metadata = result.document().metadata();
                var quality = result.qualityReport();
                long clauseNo = result.document().clauses().stream().filter(c -> c.clauseNo() != null).count();
                long hierarchy = result.document().clauses().stream().filter(c -> c.hierarchyPath() != null
                        && !c.hierarchyPath().isBlank()).count();
                report.append('|').append(pdf.getFileName().toString().replace("|", "\\|"))
                        .append(" | ").append(quality.clauseCount())
                        .append(" | ").append(quality.chunkCount())
                        .append(" | ").append(rate(clauseNo, quality.clauseCount()))
                        .append(" | ").append(rate(hierarchy, quality.clauseCount()))
                        .append(" | ").append(quality.qualityStatus()).append(" |\n");
                assertEquals("MINERU_PDF", metadata.sourceFormat().name());
                assertTrue(quality.clauseCount() > 0, "未生成 Clause: " + pdf);
                assertTrue(quality.chunkCount() > 0, "未生成 Chunk: " + pdf);
                succeeded++;
            } catch (Exception e) {
                report.append('|').append(pdf.getFileName().toString().replace("|", "\\|"))
                        .append(" | - | - | - | - | FAILED: ")
                        .append(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replace('|', '/'))
                        .append(" |\n");
            }
        }
        assertTrue(succeeded > 0, "10 份 PDF 均未完成 MinerU Dry Run");
        Path output = Path.of("target", "phase5-1-pdf-dry-run.md");
        Files.createDirectories(output.getParent());
        Files.writeString(output, report, StandardCharsets.UTF_8);
    }

    private String rate(long numerator, long denominator) {
        return denominator == 0 ? "0.00%" : String.format("%.2f%%", numerator * 100.0 / denominator);
    }
}
