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

import com.nageoffer.ai.ragent.core.parser.mineru.MinerUDocumentParser;
import com.nageoffer.ai.ragent.core.parser.model.ParsedDocument;
import com.nageoffer.ai.ragent.legal.ingest.LegalDocumentImportAdapter;
import com.nageoffer.ai.ragent.legal.ingest.LegalPdfImportService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegalPdfImportServiceTest {

    @Test
    void invokesExistingMineruParserThenLegalAdapter() {
        MinerUDocumentParser parser = mock(MinerUDocumentParser.class);
        byte[] pdf = "%PDF".getBytes(StandardCharsets.UTF_8);
        when(parser.parseStructured(org.mockito.ArgumentMatchers.same(pdf),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(ParsedDocument.of(List.of()));
        LegalDocumentImportAdapter adapter = mock(LegalDocumentImportAdapter.class);
        assertNull(new LegalPdfImportService(parser, adapter).dryRun("doc-1", "a.pdf", pdf));
        verify(adapter).importPdf("doc-1", "a.pdf", pdf, ParsedDocument.of(List.of()),
                com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode.DRY_RUN);
    }

    @Test
    void propagatesMineruFailureWithoutAttemptingFallback() {
        MinerUDocumentParser parser = mock(MinerUDocumentParser.class);
        when(parser.parseStructured(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("application/pdf"), org.mockito.ArgumentMatchers.anyMap()))
                .thenThrow(new IllegalStateException("MinerU unavailable"));

        assertThrows(IllegalStateException.class, () -> new LegalPdfImportService(
                parser, mock(LegalDocumentImportAdapter.class)).dryRun("doc-1", "a.pdf", new byte[]{1}));
    }
}
