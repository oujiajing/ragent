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
import com.nageoffer.ai.ragent.legal.model.LegalSubUnit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnumerationSequenceGapDetectorTest {

    @Test
    void detectsGapFromStructuredChildren() {
        LegalClause clause = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "3", "", "", 1)));

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(clause))).singleElement()
                .satisfies(signal -> assertThat(signal.evidence()).containsEntry("missing", List.of("2")));
    }

    @Test
    void ignoresOrdinaryNumbersAndCompleteList() {
        LegalClause complete = clause(List.of(
                new LegalSubUnit(LegalStructureType.ITEM, "1", "", "", 0),
                new LegalSubUnit(LegalStructureType.ITEM, "2", "", "", 1)));
        LegalClause ordinary = new LegalClause("b", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "标准 2024 尺寸 3", "标准 2024 尺寸 3", List.of(), "e", "e", 1, 1, 0, 1);

        assertThat(new EnumerationSequenceGapDetector().detect(List.of(complete, ordinary))).isEmpty();
    }

    private static LegalClause clause(List<LegalSubUnit> children) {
        return new LegalClause("a", "doc", LegalContentRole.NORMATIVE, LegalStructureType.CLAUSE,
                "1", "", "", "", "1.1", "", "", "", children, "e", "e", 1, 1, 0, 1);
    }
}
