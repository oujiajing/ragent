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

import com.nageoffer.ai.ragent.infra.token.HeuristicTokenCounterService;
import com.nageoffer.ai.ragent.legal.chunk.LegalChunker;
import com.nageoffer.ai.ragent.legal.clean.EmptyElementCleanupStep;
import com.nageoffer.ai.ragent.legal.clean.LegalCleaningPipeline;
import com.nageoffer.ai.ragent.legal.clean.LegalNumberWhitespaceNormalizationStep;
import com.nageoffer.ai.ragent.legal.clean.UnicodeNormalizationStep;
import com.nageoffer.ai.ragent.legal.clean.WhitespaceNormalizationStep;
import com.nageoffer.ai.ragent.legal.config.LegalIngestionProperties;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImporter;
import com.nageoffer.ai.ragent.legal.metadata.LegalMetadataExtractor;
import com.nageoffer.ai.ragent.legal.parser.DefaultLegalStructureParser;
import com.nageoffer.ai.ragent.legal.qc.LegalQualityService;

final class LegalTestFixtures {

    private LegalTestFixtures() {
    }

    static LegalIngestionProperties properties() {
        return new LegalIngestionProperties();
    }

    static CleanedTextImporter importer() {
        return importer(properties());
    }

    static CleanedTextImporter importer(LegalIngestionProperties properties) {
        LegalCleaningPipeline cleaning = new LegalCleaningPipeline(java.util.List.of(
                new UnicodeNormalizationStep(),
                new WhitespaceNormalizationStep(),
                new LegalNumberWhitespaceNormalizationStep(),
                new EmptyElementCleanupStep()));
        return new CleanedTextImporter(
                cleaning,
                new LegalMetadataExtractor(),
                new DefaultLegalStructureParser(),
                new LegalChunker(new HeuristicTokenCounterService(), properties),
                new LegalQualityService(properties));
    }
}
