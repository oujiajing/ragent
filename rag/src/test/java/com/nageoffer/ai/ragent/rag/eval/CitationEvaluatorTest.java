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

package com.nageoffer.ai.ragent.rag.eval;

import com.nageoffer.ai.ragent.legal.model.LegalEvidence;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CitationEvaluatorTest {

    @ParameterizedTest(name = "answer case {0}")
    @MethodSource("twentyCases")
    void acceptsOnlyEvidenceBackedSystemCitation(int number) {
        String clause = "JGJ 80-2016#4.3." + number;
        LegalEvidence evidence = new LegalEvidence(
                "evidence-1", "施工安全规范", "JGJ 80-2016", "4.3." + number,
                "4 / 4.3 / 4.3." + number, "CLAUSE", "明确要求内容", "chunk-1", null, 0.9f, 0.8f);
        LegalAnswerResponse.Citation citation = new LegalAnswerResponse.Citation(
                "evidence-1", "《施工安全规范》 JGJ 80-2016 第4.3." + number + "条");

        CitationEvaluator.Result result = CitationEvaluator.evaluate(
                clause, "依据证据回答 [evidence-1]", List.of(evidence), List.of(citation));

        assertTrue(result.citationCorrect());
        assertTrue(result.evidenceSupport());
    }

    @org.junit.jupiter.api.Test
    void rejectsCitationForClauseOutsideEvidence() {
        LegalEvidence evidence = new LegalEvidence(
                "evidence-1", "施工安全规范", "JGJ 80-2016", "4.3.1",
                "4 / 4.3 / 4.3.1", "CLAUSE", "栏杆要求", "chunk-1", null, 0.9f, 0.8f);
        LegalAnswerResponse.Citation citation = new LegalAnswerResponse.Citation(
                "evidence-1", "《施工安全规范》 JGJ 80-2016 第4.3.1条");

        CitationEvaluator.Result result = CitationEvaluator.evaluate(
                "JGJ 130-2011#6.6.2", "依据证据回答 [evidence-1]", List.of(evidence), List.of(citation));

        assertFalse(result.citationCorrect());
        assertFalse(result.unsupportedClaim());
    }

    private static Stream<Integer> twentyCases() {
        return IntStream.rangeClosed(1, 20).boxed();
    }
}
