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

package com.nageoffer.ai.ragent.legal.review;

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;
import com.nageoffer.ai.ragent.legal.model.LegalClause;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClauseSequenceGapDetectorTest {

    @Test
    void reportsOnlyInteriorGapWithinSameScope() {
        ClauseSequenceGapDetector detector = new ClauseSequenceGapDetector();
        List<LegalClause> clauses = List.of(
                clause("a", "4.1.1", "1", LegalContentRole.NORMATIVE),
                clause("b", "4.1.3", "1", LegalContentRole.NORMATIVE),
                clause("c", "4.2.1", "1", LegalContentRole.NORMATIVE),
                clause("d", "4.1.1", "1", LegalContentRole.COMMENTARY));

        assertThat(detector.detect(clauses)).singleElement().satisfies(signal -> {
            assertThat(signal.signalType()).isEqualTo(ReviewSignalType.CLAUSE_SEQUENCE_GAP);
            assertThat(signal.evidence()).containsEntry("expected", "4.1.2");
        });
    }

    @Test
    void doesNotTreatDuplicateOrReversedNumbersAsGap() {
        ClauseSequenceGapDetector detector = new ClauseSequenceGapDetector();
        assertThat(detector.detect(List.of(
                clause("a", "1.2", "1", LegalContentRole.NORMATIVE),
                clause("b", "1.2", "1", LegalContentRole.NORMATIVE),
                clause("c", "1.1", "1", LegalContentRole.NORMATIVE)))).isEmpty();
    }

    private static LegalClause clause(String id, String no, String chapter, LegalContentRole role) {
        return new LegalClause(id, "doc", role, LegalStructureType.CLAUSE,
                chapter, "", "", "", no, "", no, no, List.of(), "e1", "e2", 1, 1, 0, 1);
    }
}
