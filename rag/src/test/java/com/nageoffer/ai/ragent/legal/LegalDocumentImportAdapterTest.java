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

import com.nageoffer.ai.ragent.core.parser.model.HeadingBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParagraphBlock;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.core.parser.model.Provenance;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import com.nageoffer.ai.ragent.legal.ingest.LegalDocumentImportAdapter;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalDocumentImportAdapterTest {

    @Test
    void routesMineruBlocksThroughTheExistingLegalPipelineAndHashesPdf() {
        byte[] pdf = "%PDF-5.1 synthetic".getBytes(StandardCharsets.UTF_8);
        ParsedDocument parsed = ParsedDocument.of(List.of(
                new HeadingBlock(Provenance.ofFile("sample.pdf"), 1, "建设工程安全生产管理条例"),
                new ParagraphBlock(Provenance.ofFile("sample.pdf"), "第一条 为了加强建设工程安全生产管理，制定本条例。"),
                new ParagraphBlock(Provenance.ofFile("sample.pdf"), "（一）施工单位应当建立安全生产责任制；")
        ));

        CleanedTextImportResult result = new LegalDocumentImportAdapter(LegalTestFixtures.importer())
                .importPdf("pdf-doc-1", "sample.pdf", pdf, parsed, CleanedTextImportMode.DRY_RUN);

        assertEquals("MINERU_PDF", result.document().metadata().sourceFormat().name());
        assertEquals(LegalDocumentImportAdapter.PARSER_VERSION, result.document().metadata().parserVersion());
        assertTrue(result.document().metadata().fileHash().matches("[0-9a-f]{64}"));
        assertEquals("第一条", result.document().clauses().get(0).clauseNo());
        assertTrue(result.document().clauses().get(0).rawText().contains("（一）"));
    }

    @Test
    void rejectsMissingMineruOutput() {
        LegalDocumentImportAdapter adapter = new LegalDocumentImportAdapter(LegalTestFixtures.importer());
        assertThrows(IllegalArgumentException.class, () -> adapter.importPdf(
                "pdf-doc-1", "sample.pdf", new byte[]{1}, ParsedDocument.of(List.of()), CleanedTextImportMode.DRY_RUN));
    }
}
