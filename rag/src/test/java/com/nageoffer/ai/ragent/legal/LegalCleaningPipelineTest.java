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

import com.nageoffer.ai.ragent.legal.clean.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalCleaningPipelineTest {
    @Test
    void shouldRetainRawLineAndNormalizeNumberWhitespace() {
        var pipeline = new LegalCleaningPipeline(java.util.List.of(new UnicodeNormalizationStep(),
                new WhitespaceNormalizationStep(), new LegalNumberWhitespaceNormalizationStep(), new EmptyElementCleanupStep()));
        var elements = pipeline.clean("doc", "3 .1 .1  要求\n\n");
        assertEquals(1, elements.size());
        assertTrue(elements.get(0).rawText().startsWith("3 .1 .1"));
        assertEquals("3.1.1 要求", elements.get(0).normalizedText());
    }
}
