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

package com.nageoffer.ai.ragent.legal.service;

import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.legal.ingest.LegalPdfImportService;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusIndexingService;
import com.nageoffer.ai.ragent.legal.persistence.LegalCorpusPersistenceService;
import com.nageoffer.ai.ragent.legal.review.LegalReviewService;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeDocumentDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Executes the existing Legal PDF pipeline for an already-uploaded document. */
@Service
@RequiredArgsConstructor
@Slf4j
public class LegalDocumentProcessingService {

    private final LegalPdfImportService legalPdfImportService;
    private final LegalCorpusPersistenceService persistenceService;
    private final LegalCorpusIndexingService indexingService;
    private final LegalReviewService legalReviewService;

    public int process(KnowledgeDocumentDO document, byte[] pdfBytes, VectorTarget target, String actor) {
        CleanedTextImportResult result = legalPdfImportService.dryRun(document.getId(), document.getDocName(), pdfBytes);
        persistenceService.replaceUploadedDocument(document, result, actor == null ? "system" : actor);
        try {
            legalReviewService.audit(document.getId());
        } catch (RuntimeException error) {
            log.warn("Legal Chunk 已完成，但复核检测失败，保留检测失败状态, docId={}", document.getId(), error);
        }
        return indexingService.indexUploadedDocument(document.getKbId(), document.getId(), target);
    }

    public void deleteArtifacts(String documentId) {
        persistenceService.clearUploadedDocumentArtifacts(documentId);
    }

    public int reindex(KnowledgeDocumentDO document, VectorTarget target) {
        return indexingService.indexUploadedDocument(document.getKbId(), document.getId(), target);
    }
}
