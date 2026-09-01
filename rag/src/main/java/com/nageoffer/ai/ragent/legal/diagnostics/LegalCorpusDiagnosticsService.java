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

package com.nageoffer.ai.ragent.legal.diagnostics;

import com.nageoffer.ai.ragent.legal.enums.LegalContentRole;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImporter;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.model.LegalClause;
import com.nageoffer.ai.ragent.legal.model.LegalDocumentElement;
import com.nageoffer.ai.ragent.legal.model.NormalizedLegalDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class LegalCorpusDiagnosticsService {

    private static final Pattern HEADING = Pattern.compile("^(?:第[一二三四五六七八九十百零〇两]+章|第[一二三四五六七八九十百零〇两]+节|\\d{1,2}(?:\\.\\d+)?\\s+[^。！？；;]{1,30})$");
    private static final Pattern TABLE = Pattern.compile("(?:\\|.+\\||\\t.+\\t|表\\s*[A-Za-z一二三四五六七八九十0-9-]*)");
    private static final Pattern FIGURE = Pattern.compile("^(?:图|图示|图\\s*\\d|Fig\\.?\\s*\\d).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMULA = Pattern.compile(".*(?:[=≤≥Σφλμ]|式\\s*\\(?\\d).*");
    private static final Pattern METADATA = Pattern.compile("^(?:中华人民共和国|批准部门|发布部门|主编单位|参编单位|施行日期|发布日期|公告|第\\s*\\d+\\s*号).*");

    private final CleanedTextImporter importer;

    public LegalCorpusDiagnosticsService(CleanedTextImporter importer) {
        this.importer = importer;
    }

    public LegalDocumentDiagnostics analyze(Path source) throws IOException {
        String documentId = "corpus-" + Integer.toUnsignedString(source.getFileName().toString().hashCode(), 36);
        byte[] bytes = Files.readAllBytes(source);
        CleanedTextImportResult first = importer.importText(documentId, source.getFileName().toString(), bytes,
                CleanedTextImportMode.DRY_RUN);
        CleanedTextImportResult second = importer.importText(documentId, source.getFileName().toString(), bytes,
                CleanedTextImportMode.DRY_RUN);
        boolean deterministic = signature(first).equals(signature(second));
        NormalizedLegalDocument document = first.document();
        return new LegalDocumentDiagnostics(source.getFileName().toString(), first,
                duplicateGroups(source, document), unstructured(source, document), metadataWarnings(document),
                coverage(document), deterministic);
    }

    public List<LegalDocumentDiagnostics> analyzeAll(Path corpus) throws IOException {
        try (var stream = Files.list(corpus)) {
            List<Path> files = stream.filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            List<LegalDocumentDiagnostics> result = new ArrayList<>();
            for (Path file : files) result.add(analyze(file));
            return List.copyOf(result);
        }
    }

    public static String signature(CleanedTextImportResult result) {
        StringBuilder out = new StringBuilder();
        out.append(result.document().clauses().size()).append('|').append(result.chunks().size()).append('|');
        result.document().clauses().forEach(clause -> out.append(clause.clauseId()).append(':')
                .append(clause.contentRole()).append(':').append(clause.clauseNo()).append(':')
                .append(clause.hierarchyPath()).append(';'));
        result.chunks().forEach(chunk -> out.append(chunk.chunkIndex()).append(':')
                .append(chunk.metadata().parentClauseId()).append(':').append(chunk.metadata().clauseNo()).append(';'));
        out.append(result.qualityReport().qualityStatus()).append(':').append(result.qualityReport().warnings());
        return out.toString();
    }

    private List<LegalDuplicateGroup> duplicateGroups(Path source, NormalizedLegalDocument document) {
        Map<String, List<LegalClause>> byRoleNumber = new LinkedHashMap<>();
        for (LegalClause clause : document.clauses()) {
            byRoleNumber.computeIfAbsent(clause.contentRole() + "|" + clause.clauseNo(), ignored -> new ArrayList<>()).add(clause);
        }
        List<LegalDuplicateGroup> groups = new ArrayList<>();
        for (Map.Entry<String, List<LegalClause>> entry : byRoleNumber.entrySet()) {
            List<LegalClause> clauses = entry.getValue();
            if (clauses.size() < 2) continue;
            Map<String, List<LegalClause>> byText = new LinkedHashMap<>();
            clauses.forEach(clause -> byText.computeIfAbsent(normalizeForCompare(clause.normalizedText()), ignored -> new ArrayList<>()).add(clause));
            for (List<LegalClause> sameText : byText.values()) {
                if (sameText.size() > 1) {
                    groups.add(group(source, sameText, LegalDuplicateType.EXACT_DUPLICATE,
                            rawTextsIdentical(sameText) ? LegalDuplicateType.SOURCE_EXACT_DUPLICATE : LegalDuplicateType.UNKNOWN));
                }
            }
            if (byText.size() > 1) {
                groups.add(group(source, clauses, LegalDuplicateType.NEAR_DUPLICATE, LegalDuplicateType.UNKNOWN));
            }
        }
        Map<String, Set<LegalContentRole>> roles = new HashMap<>();
        Map<String, List<LegalClause>> roleClauses = new HashMap<>();
        for (LegalClause clause : document.clauses()) {
            roles.computeIfAbsent(clause.clauseNo(), ignored -> new HashSet<>()).add(clause.contentRole());
            roleClauses.computeIfAbsent(clause.clauseNo(), ignored -> new ArrayList<>()).add(clause);
        }
        for (Map.Entry<String, Set<LegalContentRole>> entry : roles.entrySet()) {
            if (entry.getValue().contains(LegalContentRole.NORMATIVE)
                    && entry.getValue().contains(LegalContentRole.COMMENTARY)) {
                groups.add(group(source, roleClauses.get(entry.getKey()), LegalDuplicateType.SAME_NUMBER_DIFFERENT_ROLE,
                        LegalDuplicateType.STRUCTURAL_VALID));
            }
        }
        return List.copyOf(groups);
    }

    private LegalDuplicateGroup group(Path source,
                                      List<LegalClause> clauses,
                                      LegalDuplicateType type,
                                      LegalDuplicateType origin) {
        LegalClause first = clauses.get(0);
        return new LegalDuplicateGroup(source.getFileName().toString(), first.contentRole(), first.clauseNo(), type, origin,
                clauses.size(), clauses.stream().limit(2).map(c -> preview(c.normalizedText(), 240)).toList());
    }

    private boolean rawTextsIdentical(List<LegalClause> clauses) {
        return clauses.stream().map(LegalClause::rawText).distinct().count() == 1;
    }

    private List<LegalUnstructuredItem> unstructured(Path source, NormalizedLegalDocument document) {
        return document.unstructuredParagraphs().stream()
                .map(element -> new LegalUnstructuredItem(source.getFileName().toString(), element.elementIndex(),
                        element.rawText(), classify(element.rawText())))
                .toList();
    }

    private UnstructuredDiagnosticType classify(String text) {
        if (text == null || text.isBlank()) return UnstructuredDiagnosticType.UNKNOWN;
        if (HEADING.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.HEADING_LIKE;
        if (TABLE.matcher(text).find()) return UnstructuredDiagnosticType.TABLE_RESIDUE;
        if (FIGURE.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.FIGURE_CAPTION;
        if (FORMULA.matcher(text).matches()) return UnstructuredDiagnosticType.FORMULA_LIKE;
        if (METADATA.matcher(text.strip()).matches()) return UnstructuredDiagnosticType.METADATA;
        if (text.contains("附录")) return UnstructuredDiagnosticType.APPENDIX_TEXT;
        return UnstructuredDiagnosticType.NORMAL_PARAGRAPH_UNKNOWN;
    }

    private List<String> metadataWarnings(NormalizedLegalDocument document) {
        List<String> warnings = new ArrayList<>(document.warnings());
        var metadata = document.metadata();
        if (metadata.docTitle() == null || metadata.docTitle().isBlank()) warnings.add("title missing");
        if (metadata.standardNo() == null || metadata.standardNo().isBlank()) warnings.add("standardNo missing");
        if (!metadata.sourceFile().matches(".*(?:19|20)\\d{2}.*")) warnings.add("year missing");
        return List.copyOf(warnings);
    }

    private double coverage(NormalizedLegalDocument document) {
        int total = document.elements().stream().mapToInt(e -> e.normalizedText().length()).sum();
        if (total == 0) return 0;
        Set<Integer> covered = new HashSet<>();
        document.elements().forEach(e -> {
            if (e.structureType() != com.nageoffer.ai.ragent.legal.enums.LegalStructureType.UNKNOWN) covered.add(e.elementIndex());
        });
        document.unstructuredParagraphs().forEach(e -> covered.add(e.elementIndex()));
        int coveredLength = document.elements().stream().filter(e -> covered.contains(e.elementIndex()))
                .mapToInt(e -> e.normalizedText().length()).sum();
        return (double) coveredLength / total;
    }

    private String normalizeForCompare(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "").replace("。", ".");
    }

    private String preview(String text, int length) {
        String value = text == null ? "" : text.replace('\n', ' ').strip();
        return value.length() <= length ? value : value.substring(0, length) + "...";
    }
}
