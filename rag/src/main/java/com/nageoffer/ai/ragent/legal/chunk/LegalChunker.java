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

package com.nageoffer.ai.ragent.legal.chunk;

import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import com.nageoffer.ai.ragent.legal.config.LegalIngestionProperties;
import com.nageoffer.ai.ragent.legal.enums.LegalChunkType;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;
import com.nageoffer.ai.ragent.legal.model.LegalChunk;
import com.nageoffer.ai.ragent.legal.model.LegalChunkMetadata;
import com.nageoffer.ai.ragent.legal.model.LegalClause;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentMetadata;
import com.nageoffer.ai.ragent.legal.model.LegalSubUnit;
import com.nageoffer.ai.ragent.legal.model.NormalizedLegalDocument;
import com.nageoffer.ai.ragent.legal.util.LegalHashes;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LegalChunker {

    private static final Pattern SENTENCE = Pattern.compile(".*?(?:[。！？!?；;]|$)", Pattern.DOTALL);

    private final TokenCounterService tokenCounter;
    private final LegalIngestionProperties properties;

    public LegalChunker(TokenCounterService tokenCounter, LegalIngestionProperties properties) {
        this.tokenCounter = tokenCounter;
        this.properties = properties;
        properties.getChunk().validate();
    }

    public List<LegalChunk> chunk(NormalizedLegalDocument document) {
        List<LegalChunk> result = new ArrayList<>();
        for (LegalClause clause : document.clauses()) {
            if (clause.normalizedText().isBlank()) continue;
            result.addAll(chunkClause(document.metadata(), clause, result.size()));
        }
        return List.copyOf(result);
    }

    private List<LegalChunk> chunkClause(LegalDocumentMetadata document,
                                         LegalClause clause,
                                         int firstChunkIndex) {
        int maxTokens = properties.getChunk().getMaxTokens();
        if (tokens(clause.normalizedText()) <= maxTokens) {
            String body = renderClauseBody(clause);
            return List.of(build(document, clause, firstChunkIndex, 0,
                    body, null, LegalChunkType.CLAUSE));
        }

        List<Unit> units = expandableUnits(clause, maxTokens);
        List<List<Unit>> groups = pack(units, maxTokens);
        List<LegalChunk> chunks = new ArrayList<>(groups.size());
        for (int part = 0; part < groups.size(); part++) {
            List<Unit> group = groups.get(part);
            String body = group.stream().map(Unit::text).reduce((a, b) -> a + "\n" + b).orElse("");
            String range = childRange(group);
            chunks.add(build(document, clause, firstChunkIndex + part, part,
                    body, range, LegalChunkType.CLAUSE_PART));
        }
        return chunks;
    }

    private String renderClauseBody(LegalClause clause) {
        if (clause.children().isEmpty()) return clause.normalizedText();
        return clause.children().stream()
                .map(child -> renderChild(child.marker(), child.normalizedText()))
                .filter(text -> !text.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private List<Unit> expandableUnits(LegalClause clause, int maxTokens) {
        List<LegalSubUnit> children = clause.children();
        if (children.isEmpty()) return List.of(new Unit(clause.normalizedText(), null));
        List<Unit> result = new ArrayList<>();
        for (LegalSubUnit child : children) {
            String rendered = renderChild(child.marker(), child.normalizedText());
            // HTML entities and cell punctuation are not sentence boundaries. Keep a table
            // intact; the existing quality gate still flags tables above the hard limit.
            if (child.structureType() == LegalStructureType.TABLE || tokens(rendered) <= maxTokens) {
                result.add(new Unit(rendered, child.marker()));
                continue;
            }
            List<String> sentences = splitSentences(child.normalizedText());
            if (sentences.size() <= 1) {
                result.add(new Unit(rendered, child.marker()));
                continue;
            }
            for (String sentence : sentences) {
                result.add(new Unit(renderChild(child.marker(), sentence), child.marker()));
            }
        }
        return result;
    }

    private List<List<Unit>> pack(List<Unit> units, int maxTokens) {
        List<List<Unit>> groups = new ArrayList<>();
        List<Unit> current = new ArrayList<>();
        int currentTokens = 0;
        for (Unit unit : units) {
            int unitTokens = tokens(unit.text());
            if (!current.isEmpty() && currentTokens + unitTokens > maxTokens) {
                groups.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
            current.add(unit);
            currentTokens += unitTokens;
            if (unitTokens > maxTokens) {
                groups.add(List.copyOf(current));
                current.clear();
                currentTokens = 0;
            }
        }
        if (!current.isEmpty()) groups.add(List.copyOf(current));
        return groups;
    }

    private LegalChunk build(LegalDocumentMetadata document,
                             LegalClause clause,
                             int chunkIndex,
                             int partIndex,
                             String body,
                             String childRange,
                             LegalChunkType type) {
        LegalChunkMetadata metadata = new LegalChunkMetadata(
                document.documentId(), document.docTitle(), document.standardNo(),
                clause.chapterNo(), clause.chapterTitle(), clause.sectionNo(), clause.sectionTitle(),
                clause.clauseNo(), clause.hierarchyPath(), clause.clauseId(), childRange,
                clause.contentRole(), type, clause.pageStart(), clause.pageEnd());
        String content = context(document, clause, body);
        String id = LegalHashes.shortHash(clause.clauseId() + ":part:" + partIndex);
        return new LegalChunk(id, chunkIndex, content, body, tokens(content), metadata);
    }

    private String context(LegalDocumentMetadata document, LegalClause clause, String body) {
        List<String> lines = new ArrayList<>();
        if (document.docTitle() != null && !document.docTitle().isBlank()) {
            String title = "《" + document.docTitle() + "》";
            if (document.standardNo() != null && !document.standardNo().isBlank()) title += " " + document.standardNo();
            lines.add(title);
        } else if (document.standardNo() != null && !document.standardNo().isBlank()) {
            lines.add(document.standardNo());
        }
        boolean hierarchyCarriesClause = clause.hierarchyPath() != null
                && !clause.hierarchyPath().isBlank()
                && clause.hierarchyPath().endsWith(clause.clauseNo());
        if (clause.hierarchyPath() != null && !clause.hierarchyPath().isBlank()) lines.add(clause.hierarchyPath());
        lines.add(hierarchyCarriesClause ? body.strip() : clause.clauseNo() + " " + body.strip());
        return String.join("\n", lines);
    }

    private List<String> splitSentences(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = SENTENCE.matcher(text.strip());
        while (matcher.find()) {
            String sentence = matcher.group().strip();
            if (!sentence.isEmpty()) result.add(sentence);
            if (matcher.end() == text.strip().length()) break;
        }
        return result;
    }

    private String childRange(List<Unit> group) {
        List<String> markers = group.stream().map(Unit::marker)
                .filter(marker -> marker != null && !marker.isBlank()).distinct().toList();
        if (markers.isEmpty()) return null;
        if (markers.size() == 1) return markers.get(0);
        return markers.get(0) + "~" + markers.get(markers.size() - 1);
    }

    private String renderChild(String marker, String text) {
        return marker == null || marker.isBlank() ? text.strip() : marker + " " + text.strip();
    }

    private int tokens(String text) {
        Integer count = tokenCounter.countTokens(text);
        return count == null ? 0 : count;
    }

    private record Unit(String text, String marker) {
    }
}
