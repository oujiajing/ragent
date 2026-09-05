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

import com.nageoffer.ai.ragent.legal.model.LegalClause;
import com.nageoffer.ai.ragent.legal.model.LegalSubUnit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects interior gaps in one clause's structured children or clearly delimited list lines. */
public final class EnumerationSequenceGapDetector {

    private static final Pattern MARKER = Pattern.compile("^\\s*(?:[（(]?)(\\d+)[）)]?\\s*(?:[、.:：]|$)(.*)$");

    public List<ReviewSignalCandidate> detect(List<LegalClause> clauses) {
        List<ReviewSignalCandidate> result = new ArrayList<>();
        for (LegalClause clause : clauses == null ? List.<LegalClause>of() : clauses) {
            List<String> markers = structuredMarkers(clause.children());
            if (markers.size() < 2) markers = lineMarkers(clause.rawText());
            if (markers.size() < 2) continue;
            List<Integer> numbers = markers.stream().map(this::parse).filter(value -> value != null).toList();
            for (int i = 1; i < numbers.size(); i++) {
                int previous = numbers.get(i - 1);
                int actual = numbers.get(i);
                if (actual <= previous + 1) continue;
                String expected = String.valueOf(previous + 1);
                String stableKey = String.join("|", clause.documentId(), "ENUM", clause.clauseId(), expected);
                result.add(new ReviewSignalCandidate(stableKey, clause.documentId(), ReviewSignalScope.CLAUSE,
                        ReviewSignalType.ENUMERATION_SEQUENCE_GAP, clause.clauseId(), List.of(clause.clauseId()), List.of(),
                        "观察到条款内分点缺口：" + expected,
                        Map.of("listPosition", i, "sequence", numbers, "missing", List.of(expected),
                                "clauseId", clause.clauseId(), "excerpt", clause.rawText())));
                break;
            }
        }
        return result;
    }

    private List<String> structuredMarkers(List<LegalSubUnit> children) {
        if (children == null) return List.of();
        return children.stream().map(LegalSubUnit::marker).filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> lineMarkers(String rawText) {
        if (rawText == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String line : rawText.split("\\R")) {
            Matcher matcher = MARKER.matcher(line);
            if (matcher.matches()) result.add(matcher.group(1));
        }
        return result;
    }

    private Integer parse(String value) {
        try {
            String digits = value.replaceAll("[^0-9]", "");
            return digits.isBlank() ? null : Integer.valueOf(digits);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
