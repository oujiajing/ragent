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

package com.nageoffer.ai.ragent.legal.parser;

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.enums.LegalStructureType;
import com.nageoffer.ai.ragent.legal.model.LegalClause;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentElement;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentMetadata;
import com.nageoffer.ai.ragent.legal.model.LegalSubUnit;
import com.nageoffer.ai.ragent.legal.model.NormalizedLegalDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class DefaultLegalStructureParser implements LegalStructureParser {

    private static final String CN_NUMBER = "[一二三四五六七八九十百零〇两]+";
    private static final Pattern LAW_CHAPTER = Pattern.compile("^(第" + CN_NUMBER + "章)\\s*(.*)$");
    private static final Pattern LAW_SECTION = Pattern.compile("^(第" + CN_NUMBER + "节)\\s*(.*)$");
    private static final Pattern LAW_ARTICLE = Pattern.compile("^(第" + CN_NUMBER + "条)\\s*(.*)$");
    private static final Pattern DECIMAL_RANGE = Pattern.compile(
            "^((?:[A-Za-z]\\.)?\\d+(?:\\.\\d+){1,4})\\s*([、～~-])\\s*((?:[A-Za-z]\\.)?\\d+(?:\\.\\d+){1,4})\\s*(.*)$");
    private static final Pattern DECIMAL = Pattern.compile("^((?:[A-Za-z]\\.)?\\d+(?:\\.\\d+){1,4})\\s*(.*)$");
    private static final Pattern NUMBERED_LINE = Pattern.compile("^(\\d{1,3})\\s+(.+)$");
    private static final Pattern COMPACT_NUMBERED_HEADING = Pattern.compile("^(\\d{1,2})([\\p{IsHan}]{1,20})$");
    private static final Pattern CHINESE_ITEM = Pattern.compile("^([（(]?" + CN_NUMBER + "[)）、])\\s*(.*)$");
    private static final Pattern DIGIT_ITEM = Pattern.compile("^([（(]?\\d{1,3}[)）、.])\\s*(.*)$");
    private static final Pattern APPENDIX = Pattern.compile("^附录\\s*([A-Za-z]|" + CN_NUMBER + ")?\\s*(.*)$");
    private static final Pattern COMMENTARY_HEADING = Pattern.compile("^(条文说明|条文解释)$");
    private static final Pattern SUPPLEMENTARY_HEADING = Pattern.compile("^本(?:标准|规范|规程)用词说明$");
    private static final Pattern SENTENCE_PUNCTUATION = Pattern.compile("[。！？；;:]$");

    @Override
    public NormalizedLegalDocument parse(LegalDocumentMetadata metadata,
                                         List<LegalDocumentElement> elements,
                                         List<String> initialWarnings) {
        ParseState state = new ParseState(metadata, initialWarnings);
        for (LegalDocumentElement element : elements) parseOne(state, element);
        state.flushClause();
        return new NormalizedLegalDocument(
                metadata,
                state.classified,
                state.clauses,
                state.unstructured,
                state.warnings);
    }

    private void parseOne(ParseState state, LegalDocumentElement element) {
        String line = element.normalizedText().strip();

        if (SUPPLEMENTARY_HEADING.matcher(line).matches()) {
            state.flushClause();
            state.regionRole = LegalContentRole.SUPPLEMENTARY;
            state.add(element, LegalStructureType.ROLE_HEADING, state.regionRole, null);
            return;
        }
        if (COMMENTARY_HEADING.matcher(line).matches()) {
            state.flushClause();
            state.regionRole = LegalContentRole.COMMENTARY;
            state.add(element, LegalStructureType.ROLE_HEADING, state.regionRole, null);
            return;
        }
        Matcher appendix = APPENDIX.matcher(line);
        if (appendix.matches()) {
            state.flushClause();
            state.regionRole = LegalContentRole.APPENDIX;
            state.chapterNo = appendix.group(1) == null ? "附录" : "附录" + appendix.group(1);
            state.chapterTitle = blankToNull(appendix.group(2));
            state.sectionNo = null;
            state.sectionTitle = null;
            state.add(element, LegalStructureType.APPENDIX_HEADING, state.regionRole, state.chapterNo);
            return;
        }

        Matcher chapter = LAW_CHAPTER.matcher(line);
        if (chapter.matches()) {
            state.flushClause();
            state.enterNormativeForStructuralBoundary();
            state.chapterNo = chapter.group(1);
            state.chapterTitle = blankToNull(chapter.group(2));
            state.sectionNo = null;
            state.sectionTitle = null;
            state.add(element, LegalStructureType.CHAPTER, state.regionRole, state.chapterNo);
            return;
        }
        Matcher section = LAW_SECTION.matcher(line);
        if (section.matches()) {
            state.flushClause();
            state.enterNormativeForStructuralBoundary();
            state.sectionNo = section.group(1);
            state.sectionTitle = blankToNull(section.group(2));
            state.add(element, LegalStructureType.SECTION, state.regionRole, state.sectionNo);
            return;
        }
        Matcher article = LAW_ARTICLE.matcher(line);
        if (article.matches()) {
            state.startClause(element, article.group(1), LegalStructureType.ARTICLE, article.group(2));
            return;
        }

        Matcher range = DECIMAL_RANGE.matcher(line);
        if (range.matches()) {
            String first = LegalNumberNormalizer.canonical(range.group(1));
            String second = LegalNumberNormalizer.canonical(range.group(3));
            String number = first + "~" + second;
            String body = range.group(4).strip();
            if (body.isBlank()) {
                if (state.currentClause != null) {
                    state.appendChild(element, LegalStructureType.PARAGRAPH, null, line);
                } else {
                    state.addUnstructured(element);
                }
                return;
            }
            LegalContentRole forcedRole = state.inlineCommentaryCandidate
                    && state.regionRole == LegalContentRole.NORMATIVE ? LegalContentRole.COMMENTARY : null;
            state.startClause(element, number, LegalStructureType.CLAUSE, body, forcedRole);
            return;
        }

        Matcher decimal = DECIMAL.matcher(line);
        if (decimal.matches()) {
            String number = LegalNumberNormalizer.canonical(decimal.group(1));
            String body = decimal.group(2).strip();
            if (body.isBlank()) {
                if (state.currentClause != null) {
                    state.appendChild(element, LegalStructureType.PARAGRAPH, null, line);
                } else {
                    state.addUnstructured(element);
                }
                return;
            }
            int levels = (int) number.chars().filter(ch -> ch == '.').count() + 1;
            if (levels == 2 && looksLikeHeading(body)) {
                state.flushClause();
                state.enterNormativeForStructuralBoundary();
                state.sectionNo = number;
                state.sectionTitle = blankToNull(body);
                state.add(element, LegalStructureType.SECTION, state.regionRole, number);
            } else {
                state.startClause(element, number,
                        levels >= 4 ? LegalStructureType.SUBCLAUSE : LegalStructureType.CLAUSE, body);
            }
            return;
        }

        Matcher numbered = NUMBERED_LINE.matcher(line);
        if (numbered.matches()) {
            if (state.currentClause != null && looksLikeChapterHeading(numbered.group(2))
                    && numbered.group(1).length() <= 2) {
                state.flushClause();
                state.enterNormativeForStructuralBoundary();
                state.chapterNo = numbered.group(1);
                state.chapterTitle = numbered.group(2).strip();
                state.sectionNo = null;
                state.sectionTitle = null;
                state.add(element, LegalStructureType.CHAPTER, state.regionRole, state.chapterNo);
            } else if (state.currentClause != null) {
                state.appendChild(element, LegalStructureType.ITEM, numbered.group(1), numbered.group(2));
            } else if (looksLikeChapterHeading(numbered.group(2))) {
                state.enterNormativeIfFrontMatter();
                state.chapterNo = numbered.group(1);
                state.chapterTitle = numbered.group(2).strip();
                state.sectionNo = null;
                state.sectionTitle = null;
                state.add(element, LegalStructureType.CHAPTER, state.regionRole, state.chapterNo);
            } else {
                state.addUnstructured(element);
            }
            return;
        }

        Matcher compactHeading = COMPACT_NUMBERED_HEADING.matcher(line);
        if (compactHeading.matches() && state.currentClause == null && looksLikeChapterHeading(compactHeading.group(2))) {
            state.enterNormativeIfFrontMatter();
            state.chapterNo = compactHeading.group(1);
            state.chapterTitle = compactHeading.group(2).strip();
            state.sectionNo = null;
            state.sectionTitle = null;
            state.add(element, LegalStructureType.CHAPTER, state.regionRole, state.chapterNo);
            return;
        }

        Matcher chineseItem = CHINESE_ITEM.matcher(line);
        if (chineseItem.matches() && state.currentClause != null) {
            state.appendChild(element, LegalStructureType.ITEM, chineseItem.group(1), chineseItem.group(2));
            return;
        }
        Matcher digitItem = DIGIT_ITEM.matcher(line);
        if (digitItem.matches() && state.currentClause != null) {
            state.appendChild(element, LegalStructureType.ITEM, digitItem.group(1), digitItem.group(2));
            return;
        }

        if (state.currentClause != null) {
            state.appendChild(element, LegalStructureType.PARAGRAPH, null, line);
        } else {
            state.addUnstructured(element);
        }
    }

    private boolean looksLikeHeading(String body) {
        return body != null && !body.isBlank() && body.length() <= 30
                && !SENTENCE_PUNCTUATION.matcher(body).find()
                && !containsNormativeVerb(body);
    }

    private boolean looksLikeChapterHeading(String body) {
        return looksLikeHeading(body) && body.length() <= 20;
    }

    private boolean containsNormativeVerb(String text) {
        return text.contains("应当") || text.contains("应 ") || text.startsWith("应")
                || text.contains("必须") || text.contains("不得") || text.contains("宜 ")
                || text.startsWith("为在") || text.startsWith("为加强") || text.startsWith("为规范");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static final class ParseState {
        private final LegalDocumentMetadata metadata;
        private final boolean inlineCommentaryCandidate;
        private final List<LegalDocumentElement> classified = new ArrayList<>();
        private final List<LegalClause> clauses = new ArrayList<>();
        private final List<LegalDocumentElement> unstructured = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private LegalContentRole regionRole = LegalContentRole.FRONT_MATTER;
        private String chapterNo;
        private String chapterTitle;
        private String sectionNo;
        private String sectionTitle;
        private ClauseBuilder currentClause;
        private LegalClause lastClause;

        private ParseState(LegalDocumentMetadata metadata, List<String> initialWarnings) {
            this.metadata = metadata;
            this.inlineCommentaryCandidate = metadata.sourceFile().contains("附条文说明");
            if (initialWarnings != null) warnings.addAll(initialWarnings);
        }

        private void enterNormativeIfFrontMatter() {
            if (regionRole == LegalContentRole.FRONT_MATTER || regionRole == LegalContentRole.UNKNOWN) {
                regionRole = LegalContentRole.NORMATIVE;
            }
        }

        private void enterNormativeForStructuralBoundary() {
            if (regionRole == LegalContentRole.FRONT_MATTER
                    || regionRole == LegalContentRole.UNKNOWN
                    || regionRole == LegalContentRole.APPENDIX) {
                regionRole = LegalContentRole.NORMATIVE;
            }
        }

        private void startClause(LegalDocumentElement element,
                                 String clauseNo,
                                 LegalStructureType type,
                                 String body) {
            startClause(element, clauseNo, type, body, null);
        }

        private void startClause(LegalDocumentElement element,
                                 String clauseNo,
                                 LegalStructureType type,
                                 String body,
                                 LegalContentRole forcedRole) {
            flushClause();
            enterNormativeIfFrontMatter();
            LegalContentRole clauseRole = forcedRole == null ? regionRole : forcedRole;
            if (forcedRole == null && inlineCommentaryCandidate && regionRole == LegalContentRole.NORMATIVE
                    && lastClause != null
                    && lastClause.contentRole() == LegalContentRole.NORMATIVE
                    && Objects.equals(lastClause.clauseNo(), clauseNo)) {
                clauseRole = LegalContentRole.COMMENTARY;
            } else if (forcedRole == null && inlineCommentaryCandidate && regionRole == LegalContentRole.NORMATIVE
                    && lastClause != null && lastClause.contentRole() == LegalContentRole.COMMENTARY) {
                clauseRole = LegalContentRole.NORMATIVE;
            }
            currentClause = new ClauseBuilder(
                    metadata.documentId() + ":c:" + clauses.size(),
                    metadata.documentId(), clauseRole, type,
                    chapterNo, chapterTitle, sectionNo, sectionTitle,
                    clauseNo, hierarchy(clauseNo), element);
            currentClause.add(element, LegalStructureType.PARAGRAPH, null, body);
            add(element, type, clauseRole, clauseNo);
        }

        private void appendChild(LegalDocumentElement element,
                                 LegalStructureType type,
                                 String marker,
                                 String body) {
            currentClause.add(element, type, marker, body);
            add(element, type, currentClause.contentRole, marker);
        }

        private void flushClause() {
            if (currentClause == null) return;
            lastClause = currentClause.build();
            clauses.add(lastClause);
            currentClause = null;
        }

        private void addUnstructured(LegalDocumentElement element) {
            LegalContentRole role = regionRole;
            add(element, LegalStructureType.PARAGRAPH, role, null);
            if (role == LegalContentRole.NORMATIVE || role == LegalContentRole.UNKNOWN) {
                unstructured.add(element.classified(LegalStructureType.PARAGRAPH, role, null));
            }
        }

        private void add(LegalDocumentElement element,
                         LegalStructureType type,
                         LegalContentRole role,
                         String number) {
            classified.add(element.classified(type, role, number));
        }

        private String hierarchy(String clauseNo) {
            List<String> parts = new ArrayList<>();
            appendPath(parts, chapterNo, chapterTitle);
            appendPath(parts, sectionNo, sectionTitle);
            if (clauseNo != null && !clauseNo.isBlank()) parts.add(clauseNo);
            return String.join(" / ", parts);
        }

        private void appendPath(List<String> parts, String no, String title) {
            if (no == null || no.isBlank()) return;
            parts.add(title == null || title.isBlank() ? no : no + " " + title);
        }
    }

    private static final class ClauseBuilder {
        private final String id;
        private final String documentId;
        private final LegalContentRole contentRole;
        private final LegalStructureType type;
        private final String chapterNo;
        private final String chapterTitle;
        private final String sectionNo;
        private final String sectionTitle;
        private final String clauseNo;
        private final String hierarchy;
        private final String firstElementId;
            private String lastElementId;
        private int sourceStartOffset;
        private int sourceEndOffset;
        private final List<String> rawLines = new ArrayList<>();
        private final List<String> normalizedBodies = new ArrayList<>();
        private final List<LegalSubUnit> children = new ArrayList<>();

        private ClauseBuilder(String id,
                              String documentId,
                              LegalContentRole contentRole,
                              LegalStructureType type,
                              String chapterNo,
                              String chapterTitle,
                              String sectionNo,
                              String sectionTitle,
                              String clauseNo,
                              String hierarchy,
                              LegalDocumentElement first) {
            this.id = id;
            this.documentId = documentId;
            this.contentRole = contentRole;
            this.type = type;
            this.chapterNo = chapterNo;
            this.chapterTitle = chapterTitle;
            this.sectionNo = sectionNo;
            this.sectionTitle = sectionTitle;
            this.clauseNo = clauseNo;
            this.hierarchy = hierarchy;
            this.firstElementId = first.elementId();
            this.lastElementId = first.elementId();
            this.sourceStartOffset = first.sourceStartOffset();
            this.sourceEndOffset = first.sourceEndOffset();
        }

        private void add(LegalDocumentElement element,
                         LegalStructureType childType,
                         String marker,
                         String body) {
            rawLines.add(element.rawText());
            String normalized = body == null ? "" : body.strip();
            if (!normalized.isBlank()) {
                normalizedBodies.add(normalized);
                children.add(new LegalSubUnit(childType, marker, element.rawText(), normalized, element.elementIndex()));
            }
            lastElementId = element.elementId();
            sourceStartOffset = Math.min(sourceStartOffset, element.sourceStartOffset());
            sourceEndOffset = Math.max(sourceEndOffset, element.sourceEndOffset());
        }

        private LegalClause build() {
            return new LegalClause(
                    id, documentId, contentRole, type,
                    chapterNo, chapterTitle, sectionNo, sectionTitle,
                    clauseNo, hierarchy,
                    String.join("\n", rawLines),
                    String.join("\n", normalizedBodies),
                    children,
                    firstElementId, lastElementId,
                    null, null, sourceStartOffset, sourceEndOffset);
        }
    }
}
