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
import com.nageoffer.ai.ragent.core.parser.mineru.MinerUResultUnpacker;
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
    private final MinerUResultUnpacker resultUnpacker;

    public LegalPdfImportService(MinerUDocumentParser minerUParser, LegalDocumentImportAdapter adapter,
                                 LegalSectionFilter sectionFilter) {
        this.minerUParser = minerUParser;
        this.adapter = adapter;
        this.sectionFilter = sectionFilter;
        this.resultUnpacker = null;
    }

    /** Backward-compatible constructor for focused unit tests and non-Spring callers. */
    public LegalPdfImportService(MinerUDocumentParser minerUParser, LegalDocumentImportAdapter adapter) {
        this(minerUParser, adapter, new LegalSectionFilter());
    }

    @Autowired
    public LegalPdfImportService(MinerUDocumentParser minerUParser, LegalDocumentImportAdapter adapter,
                                 LegalSectionFilter sectionFilter, MinerUResultUnpacker resultUnpacker) {
        this.minerUParser = minerUParser;
        this.adapter = adapter;
        this.sectionFilter = sectionFilter;
        this.resultUnpacker = resultUnpacker;
    }

    public CleanedTextImportResult dryRun(String documentId, String sourceFile, byte[] pdfBytes) {
        ParsedDocument parsed = minerUParser.parseStructured(pdfBytes, "application/pdf", Map.of(
                MinerUDocumentParser.OPT_SOURCE_FILE, sourceFile,
                MinerUDocumentParser.OPT_DOCUMENT_ID, documentId));
        ParsedDocument filtered = sectionFilter.filter(documentId, parsed).document();
        return adapter.importPdf(documentId, sourceFile, pdfBytes, filtered, CleanedTextImportMode.DRY_RUN);
    }

    /** Replays a previously validated MinerU result through the production legal pipeline. */
    public CleanedTextImportResult dryRunCached(String documentId, String sourceFile, byte[] pdfBytes,
                                                byte[] resultZip) {
        if (resultUnpacker == null) throw new IllegalStateException("缓存 MinerU 结果仅可在 Spring 生产入口使用");
        ParsedDocument parsed = resultUnpacker.unpack(resultZip, sourceFile, documentId);
        ParsedDocument filtered = sectionFilter.filter(documentId, parsed).document();
        return adapter.importPdf(documentId, sourceFile, pdfBytes, filtered, CleanedTextImportMode.DRY_RUN);
    }
}
