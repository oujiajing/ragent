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

package com.nageoffer.ai.ragent.legal.ingest;

import com.nageoffer.ai.ragent.core.parser.mineru.MinerUDocumentParser;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.filter.LegalSectionFilter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

/** Executes the Phase 5.1 PDF dry-run chain using the existing MinerU parser. */
@Service
public class LegalPdfImportService {

    private final MinerUDocumentParser minerUParser;
    private final LegalDocumentImportAdapter adapter;
    private final LegalSectionFilter sectionFilter;

    @Autowired
    public LegalPdfImportService(MinerUDocumentParser minerUParser, LegalDocumentImportAdapter adapter,
                                 LegalSectionFilter sectionFilter) {
        this.minerUParser = minerUParser;
        this.adapter = adapter;
        this.sectionFilter = sectionFilter;
    }

    /** Backward-compatible constructor for focused unit tests and non-Spring callers. */
    public LegalPdfImportService(MinerUDocumentParser minerUParser, LegalDocumentImportAdapter adapter) {
        this(minerUParser, adapter, new LegalSectionFilter());
    }

    public CleanedTextImportResult dryRun(String documentId, String sourceFile, byte[] pdfBytes) {
        ParsedDocument parsed = minerUParser.parseStructured(pdfBytes, "application/pdf", Map.of(
                MinerUDocumentParser.OPT_SOURCE_FILE, sourceFile,
                MinerUDocumentParser.OPT_DOCUMENT_ID, documentId));
        ParsedDocument filtered = sectionFilter.filter(documentId, parsed).document();
        return adapter.importPdf(documentId, sourceFile, pdfBytes, filtered, CleanedTextImportMode.DRY_RUN);
    }
}
