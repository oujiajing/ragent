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

import com.nageoffer.ai.ragent.legal.chunk.LegalChunker;
import com.nageoffer.ai.ragent.legal.clean.LegalCleaningPipeline;
import com.nageoffer.ai.ragent.legal.metadata.LegalMetadataExtractor;
import com.nageoffer.ai.ragent.legal.metadata.MetadataExtractionResult;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.model.LegalChunk;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentElement;
import com.nageoffer.ai.ragent.legal.model.NormalizedLegalDocument;
import com.nageoffer.ai.ragent.legal.parser.LegalStructureParser;
import com.nageoffer.ai.ragent.legal.qc.LegalQualityService;
import com.nageoffer.ai.ragent.legal.util.LegalHashes;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CleanedTextImporter {

    public static final String PARSER_VERSION = "legal-txt-parser/1.0.0";

    private final LegalCleaningPipeline cleaningPipeline;
    private final LegalMetadataExtractor metadataExtractor;
    private final LegalStructureParser structureParser;
    private final LegalChunker chunker;
    private final LegalQualityService qualityService;

    public CleanedTextImporter(LegalCleaningPipeline cleaningPipeline,
                               LegalMetadataExtractor metadataExtractor,
                               LegalStructureParser structureParser,
                               LegalChunker chunker,
                               LegalQualityService qualityService) {
        this.cleaningPipeline = cleaningPipeline;
        this.metadataExtractor = metadataExtractor;
        this.structureParser = structureParser;
        this.chunker = chunker;
        this.qualityService = qualityService;
    }

    public CleanedTextImportResult importText(String documentId,
                                              String sourceFile,
                                              byte[] rawBytes,
                                              CleanedTextImportMode mode) {
        if (mode == null) throw new IllegalArgumentException("导入模式不能为空");
        if (rawBytes == null || rawBytes.length == 0) {
            throw new IllegalArgumentException("TXT bytes 不能为空");
        }
        String rawText = decodeUtf8(rawBytes);
        String fileHash = LegalHashes.sha256(rawBytes);
        List<LegalDocumentElement> elements = cleaningPipeline.clean(documentId, rawText);
        String normalizedText = String.join("\n", elements.stream().map(LegalDocumentElement::normalizedText).toList());
        MetadataExtractionResult extracted = metadataExtractor.extract(
                documentId, sourceFile, rawBytes, normalizedText, PARSER_VERSION, fileHash);
        NormalizedLegalDocument normalized = structureParser.parse(
                extracted.metadata(), elements, extracted.warnings());
        List<LegalChunk> chunks = chunker.chunk(normalized);
        return new CleanedTextImportResult(normalized, chunks, qualityService.assess(normalized, chunks), normalizedText);
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("cleaned TXT 不是有效 UTF-8", e);
        }
    }
}
