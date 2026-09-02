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

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Deterministic checks for system-generated citations and answer grounding markers. */
public final class CitationEvaluator {

    private static final Pattern STANDARD_PATTERN = Pattern.compile("(?:GB|JGJ(?:/T)?|CJJ)\\s*\\d+(?:[-/]\\d{4})?");

    private CitationEvaluator() {
    }

    public record Result(boolean citationCorrect, boolean evidenceSupport, boolean unsupportedClaim) {
    }

    public static Result evaluate(String expectedClause,
                                  String answer,
                                  List<LegalEvidence> evidence,
                                  List<LegalAnswerResponse.Citation> citations) {
        Set<String> evidenceIds = evidence == null ? Set.of()
                : evidence.stream().filter(e -> e != null).map(LegalEvidence::evidenceId).collect(Collectors.toSet());
        boolean citationCorrect = citations != null && citations.stream()
                .filter(c -> c != null && evidenceIds.contains(c.evidenceId()))
                .map(c -> evidence.stream().filter(e -> c.evidenceId().equals(e.evidenceId())).findFirst().orElse(null))
                .anyMatch(e -> e != null && expectedClause != null
                        && expectedClause.equals(e.standardNo() + "#" + e.clauseNo()));
        boolean evidenceSupport = evidence != null && evidence.stream()
                .anyMatch(e -> e != null && e.content() != null && !e.content().isBlank());
        boolean unsupportedClaim = answer != null && STANDARD_PATTERN.matcher(answer).results()
                .map(match -> match.group().replaceAll("\\s+", " ").trim())
                .anyMatch(standard -> evidence == null || evidence.stream().noneMatch(e ->
                        e != null && e.standardNo() != null && e.standardNo().replaceAll("\\s+", " ").equals(standard)));
        return new Result(citationCorrect, evidenceSupport, unsupportedClaim);
    }
}
