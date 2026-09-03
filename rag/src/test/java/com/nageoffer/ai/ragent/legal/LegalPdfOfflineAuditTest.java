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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.parser.image.ImageParseProperties;
import com.nageoffer.ai.ragent.core.parser.mineru.MinerUResultUnpacker;
import com.nageoffer.ai.ragent.core.parser.model.*;
import com.nageoffer.ai.ragent.infra.vlm.VlmService;
import com.nageoffer.ai.ragent.legal.filter.LegalSectionFilter;
import com.nageoffer.ai.ragent.legal.ingest.CleanedTextImportMode;
import com.nageoffer.ai.ragent.legal.ingest.LegalDocumentImportAdapter;
import com.nageoffer.ai.ragent.legal.model.CleanedTextImportResult;
import com.nageoffer.ai.ragent.legal.util.LegalHashes;
import com.nageoffer.ai.ragent.rag.dto.StoredFileDTO;
import com.nageoffer.ai.ragent.rag.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Offline diagnostic harness: no Spring context, MinerU client, database or embedding service. */
class LegalPdfOfflineAuditTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private static final Pattern CLAUSE_PREFIX = Pattern.compile(
            "^(?:第[一二三四五六七八九十百零〇两]+条|(?:[A-Z]\\.)?\\d+(?:\\.\\d+)+)\\s*");

    @Test
    @EnabledIfSystemProperty(named = "legal.pdf.cache.dir", matches = ".+")
    void replayCachedMineruResultsAndExportEvidence() throws Exception {
        long started = System.nanoTime();
        Path cache = Path.of(System.getProperty("legal.pdf.cache.dir")).toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("legal.pdf.audit.out", "target/legal-pdf-audit")).toAbsolutePath();
        Files.createDirectories(output);
        JsonNode expectations;
        try (var input = System.getProperty("legal.pdf.audit.expectations") == null
                ? getClass().getResourceAsStream("/legal/pdf-audit-expectations.json")
                : Files.newInputStream(Path.of(System.getProperty("legal.pdf.audit.expectations")))) {
            assertNotNull(input);
            expectations = json.readTree(input);
        }
        StringBuilder report = new StringBuilder("# PDF 离线质量诊断\n\n")
                .append("本次回放网络调用=0；无 Spring/数据库/Embedding/VLM。生产解析、过滤、清洗、分块实现保持原样。\n\n")
                .append("人工标注的正文起止位置独立于 contentRole。页码为缓存 origin.pdf 的物理页码（JSON page_idx+1），重复匹配列出候选页，不猜唯一页。\n\n")
                .append("|样本|Clause 前/后|Chunk 前/后|正文误删 Block|非正文残留 Block|疑似噪声 Chunk|未覆盖正文 Block|条号/层级字段完整率|\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---|\n");
        List<Map<String, Object>> summaries = new ArrayList<>();
        int failures = 0;
        for (JsonNode expected : expectations) {
            Path sample = cache.resolve(expected.path("sha256").asText());
            assertTrue(Files.exists(sample.resolve("manifest.json")), "Missing cached sample: " + sample);
            JsonNode manifest = json.readTree(Files.readAllBytes(sample.resolve("manifest.json")));
            byte[] pdf = Files.readAllBytes(Path.of(manifest.path("pdfPath").asText()));
            byte[] zip = Files.readAllBytes(sample.resolve("result.zip"));
            assertEquals(expected.path("sha256").asText(), LegalHashes.sha256(pdf), "PDF changed");
            assertEquals(manifest.path("zipSha256").asText(), LegalHashes.sha256(zip), "Cache changed");

            FileStorageService storage = mock(FileStorageService.class);
            VlmService vlm = mock(VlmService.class);
            when(storage.uploadAsset(any(byte[].class), anyString(), anyString())).thenAnswer(call ->
                    StoredFileDTO.builder().url("offline-asset:" + LegalHashes.sha256(call.getArgument(0))).build());
            when(storage.getPublicUrl(anyString())).thenAnswer(call -> call.getArgument(0));
            ImageParseProperties imageProperties = new ImageParseProperties();
            imageProperties.setEmbeddedDescribeEnabled(false);
            String documentId = "audit" + expected.path("sha256").asText().substring(0, 12);
            ParsedDocument parsed = new MinerUResultUnpacker(storage, vlm, imageProperties)
                    .unpack(zip, manifest.path("sourceFile").asText(), documentId);
            verifyNoInteractions(vlm);
            var filter = new LegalSectionFilter().filter(documentId, parsed);
            var adapter = new LegalDocumentImportAdapter(LegalTestFixtures.importer());
            var before = adapter.importPdf(documentId, manifest.path("sourceFile").asText(), pdf, parsed, CleanedTextImportMode.DRY_RUN);
            var after = adapter.importPdf(documentId, manifest.path("sourceFile").asText(), pdf, filter.document(), CleanedTextImportMode.DRY_RUN);
            JsonNode sourcePages = json.readTree(Files.readAllBytes(sample.resolve("content-list.json")));
            int start = boundary(parsed.blocks(), expected.path("bodyStart").asText(), 0);
            int end = expected.path("bodyEnd").asText().isBlank() ? parsed.blocks().size()
                    : boundary(parsed.blocks(), expected.path("bodyEnd").asText(), start + 1);
            int nonBodyStart = expected.path("nonBodyStart").asText().isBlank() ? start
                    : boundary(parsed.blocks(), expected.path("nonBodyStart").asText(), 0);
            assertTrue(nonBodyStart <= start && start < end, "Invalid manual boundary annotation");
            Set<Block> kept = Collections.newSetFromMap(new IdentityHashMap<>());
            kept.addAll(filter.document().blocks());
            String allChunks = compact(after.chunks().stream().map(c -> c.content()).reduce("", (a, b) -> a + "\n" + b));
            List<Map<String, Object>> decisions = new ArrayList<>();
            Set<String> noisyChunkIds = new HashSet<>();
            int lostBody = 0, retainedNoise = 0, uncoveredBody = 0, tableCount = 0, uncoveredTables = 0;
            int removedIndex = 0;
            for (int index = 0; index < parsed.blocks().size(); index++) {
                Block block = parsed.blocks().get(index);
                String text = text(block);
                boolean body = index >= start && index < end;
                boolean forbidden = index >= nonBodyStart && !body;
                boolean retained = kept.contains(block);
                boolean payload = !(block instanceof HeadingBlock) && !(block instanceof ImageBlock) && compact(text).length() >= 12;
                String normalizedPayload = compact(CLAUSE_PREFIX.matcher(text.strip()).replaceFirst(""));
                boolean covered = !payload || allChunks.contains(normalizedPayload);
                if (body && !retained) lostBody++;
                if (forbidden && retained) retainedNoise++;
                if (body && payload && !covered) uncoveredBody++;
                if (body && (block instanceof HtmlTableBlock || block instanceof TableBlock)) {
                    tableCount++;
                    if (!covered) uncoveredTables++;
                }
                if (forbidden && retained && payload) {
                    // Match actual excluded-region content, never a broad keyword such as '附录'.
                    String probe = normalizedPayload.substring(0, Math.min(100, normalizedPayload.length()));
                    after.chunks().stream().filter(c -> compact(c.content()).contains(probe))
                            .forEach(c -> noisyChunkIds.add(c.chunkId()));
                }
                Map<String, Object> decision = new LinkedHashMap<>();
                decision.put("blockIndex", index);
                decision.put("blockType", block.getClass().getSimpleName());
                decision.put("expectedRegion", body ? "BODY" : forbidden ? "NON_BODY" : "COVER_UNSCORED");
                decision.put("retained", retained);
                decision.put("chunkTextCovered", covered);
                decision.put("pageCandidates", pages(text, sourcePages));
                decision.put("text", text);
                if (!retained) decision.put("filterLog", filter.logs().get(removedIndex++));
                decisions.add(decision);
            }
            assertEquals(filter.logs().size(), removedIndex);
            Set<String> clauseNos = new HashSet<>();
            after.document().clauses().forEach(c -> clauseNos.add(c.clauseNo()));
            List<String> missingClauses = new ArrayList<>();
            expected.path("expectedClauses").forEach(c -> { if (!clauseNos.contains(c.asText())) missingClauses.add(c.asText()); });
            long blankNo = after.document().clauses().stream().filter(c -> c.clauseNo() == null || c.clauseNo().isBlank()).count();
            long blankHierarchy = after.document().clauses().stream().filter(c -> c.hierarchyPath() == null || c.hierarchyPath().isBlank()).count();
            long oversized = after.chunks().stream().filter(c -> c.tokenCount() > 600).count();
            long empty = after.chunks().stream().filter(c -> c.sourceText().isBlank()).count();
            List<String> hierarchyMismatch = after.document().clauses().stream()
                    .filter(c -> c.chapterNo() != null && c.chapterNo().matches("\\d+")
                            && c.clauseNo().matches("\\d+(?:\\.\\d+){2,}")
                            && !c.clauseNo().startsWith(c.chapterNo() + "."))
                    .map(c -> c.clauseNo() + " @ " + c.hierarchyPath()).toList();
            boolean pass = lostBody == 0 && retainedNoise == 0 && noisyChunkIds.isEmpty() && uncoveredBody == 0
                    && missingClauses.isEmpty() && blankNo == 0 && blankHierarchy == 0 && oversized == 0 && empty == 0
                    && !after.chunks().isEmpty() && hierarchyMismatch.isEmpty();
            if (!pass) failures++;
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("sample", expected.path("label").asText());
            summary.put("source", manifest);
            summary.put("status", pass ? "PASS_SAMPLED_CHECKS" : "REVIEW_REQUIRED");
            summary.put("beforeClauses", before.document().clauses().size());
            summary.put("afterClauses", after.document().clauses().size());
            summary.put("beforeChunks", before.chunks().size());
            summary.put("afterChunks", after.chunks().size());
            summary.put("bodyBlocksRemoved", lostBody);
            summary.put("nonBodyBlocksRetained", retainedNoise);
            summary.put("noiseChunkIds", noisyChunkIds);
            summary.put("bodyBlocksNotFullyCovered", uncoveredBody);
            summary.put("bodyTables", tableCount);
            summary.put("tablesNotFullyCovered", uncoveredTables);
            summary.put("missingExpectedClauses", missingClauses);
            summary.put("hierarchyMismatchCandidates", hierarchyMismatch);
            summary.put("oversizedChunks", oversized);
            summary.put("emptyChunks", empty);
            summary.put("blankClauseNo", blankNo);
            summary.put("blankHierarchy", blankHierarchy);
            summaries.add(summary);
            Path sampleOut = output.resolve(expected.path("sha256").asText());
            Files.createDirectories(sampleOut);
            writeJson(sampleOut.resolve("before.json"), export(before));
            writeJson(sampleOut.resolve("after.json"), export(after));
            writeJson(sampleOut.resolve("block-decisions.json"), decisions);
            writeJson(sampleOut.resolve("summary.json"), summary);
            Files.writeString(sampleOut.resolve("cleaned.txt"), after.canonicalSourceText(), StandardCharsets.UTF_8);
            StringBuilder evidence = new StringBuilder("# " + expected.path("label").asText() + " 内容对照\n\n");
            for (var d : decisions) {
                evidence.append("## Block ").append(d.get("blockIndex")).append(" · ").append(d.get("expectedRegion"))
                        .append(" · retained=").append(d.get("retained")).append(" · pages=").append(d.get("pageCandidates"))
                        .append("\n\n").append(d.get("text")).append("\n\n");
            }
            evidence.append("# 分块结果\n\n");
            after.chunks().forEach(c -> evidence.append("## Chunk ").append(c.chunkIndex()).append(" · ")
                    .append(c.metadata().clauseNo()).append(" · tokens=").append(c.tokenCount()).append("\n\n")
                    .append(c.content()).append("\n\n"));
            Files.writeString(sampleOut.resolve("evidence.md"), evidence, StandardCharsets.UTF_8);
            report.append('|').append(expected.path("label").asText()).append('|')
                    .append(before.document().clauses().size()).append('/').append(after.document().clauses().size()).append('|')
                    .append(before.chunks().size()).append('/').append(after.chunks().size()).append('|')
                    .append(lostBody).append('|').append(retainedNoise).append('|').append(noisyChunkIds.size()).append('|')
                    .append(uncoveredBody).append('|').append(rate(blankNo, after.document().clauses().size())).append('/')
                    .append(rate(blankHierarchy, after.document().clauses().size())).append("|\n");
        }
        var probes = boundaryProbes();
        writeJson(output.resolve("boundary-probes.json"), probes);
        report.append("\n## 合成边界用例（不计入真实 PDF 样本）\n\n");
        probes.forEach(p -> report.append("- ").append(p.get("name")).append(": ").append(p.get("pass")).append("\n"));
        report.append("\nREVIEW_REQUIRED 样本数：").append(failures).append("；运行秒数：")
                .append(String.format(Locale.ROOT, "%.3f", (System.nanoTime() - started) / 1e9)).append("。\n\n")
                .append("未覆盖正文 Block 使用去空白、去 HTML 标签后的完整文本匹配，属于待人工确认项，分块边界/标记变化也可能产生告警。字段完整率不等于识别召回率。\n");
        writeJson(output.resolve("summary.json"), summaries);
        Files.writeString(output.resolve("REPORT.md"), report, StandardCharsets.UTF_8);
        System.out.println("OFFLINE_AUDIT " + output + " review=" + failures + " network=0");
        if (Boolean.getBoolean("legal.pdf.audit.strict")) {
            int reviewCount = failures;
            assertAll("offline quality gate",
                    () -> assertEquals(0, reviewCount, "Quality gate failed; inspect exported evidence"),
                    () -> assertTrue(probes.stream().allMatch(p -> Boolean.TRUE.equals(p.get("pass"))), "Boundary probe failed"));
        }
    }

    @Test
    void manualBoundaryLookupIsIndependentOfFilterLabels() {
        var p = Provenance.ofFile("test");
        var blocks = List.<Block>of(new HeadingBlock(p, 1, "目 次"), new ParagraphBlock(p, "1 总则 ... 1"),
                new HeadingBlock(p, 1, "1 总 则"));
        assertEquals(2, boundary(blocks, "1总则", 0));
        assertThrows(AssertionError.class, () -> boundary(blocks, "不存在", 0));
    }

    private static List<Map<String, Object>> boundaryProbes() {
        var p = Provenance.ofFile("synthetic");
        List<Map<String, Object>> result = new ArrayList<>();
        for (String heading : List.of("TABLE OF CONTENTS", "Appendix A", "本标准用词说明")) {
            var input = ParsedDocument.of(List.of(new HeadingBlock(p, 1, "1 总则"), new ParagraphBlock(p, "1.0.1 正文应保留。"),
                    new HeadingBlock(p, 1, heading), new ParagraphBlock(p, "NON_BODY_SENTINEL")));
            var filtered = new LegalSectionFilter().filter("probe", input).document();
            result.add(Map.of("name", heading, "pass", filtered.blocks().stream().noneMatch(b -> text(b).contains("NON_BODY_SENTINEL"))));
        }
        return result;
    }

    private static int boundary(List<Block> blocks, String expected, int from) {
        for (int i = from; i < blocks.size(); i++) if (compact(text(blocks.get(i))).equals(compact(expected))) return i;
        throw new AssertionError("Manual anchor not found: " + expected);
    }

    private static List<Integer> pages(String text, JsonNode items) {
        String key = compact(text);
        if (key.isBlank()) return List.of();
        Set<Integer> matches = new TreeSet<>();
        for (JsonNode item : items) {
            String candidate = compact(item.path("text").asText(item.path("table_body").asText("")));
            if (!candidate.isEmpty() && (candidate.equals(key) || (key.length() >= 20 && candidate.contains(key)))) {
                if (item.has("page_idx")) matches.add(item.path("page_idx").asInt() + 1);
            }
        }
        return List.copyOf(matches);
    }

    private static String compact(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC)
                .replaceAll("<[^>]*>", "").replaceAll("[\\s\\p{Z}]+", "");
    }

    private static String text(Block block) {
        if (block instanceof HeadingBlock h) return h.text();
        if (block instanceof ParagraphBlock p) return p.text();
        if (block instanceof HtmlTableBlock t) return t.html();
        if (block instanceof TableBlock t) return String.join(" ", t.headers()) + "\n" + t.rows().stream().map(r -> String.join(" ", r)).reduce("", (a,b) -> a + "\n" + b);
        if (block instanceof ListBlock l) return String.join("\n", l.items());
        if (block instanceof CodeBlock c) return c.code();
        if (block instanceof ImageBlock i) return i.caption() == null ? "" : i.caption();
        return "";
    }

    private static Map<String, Object> export(CleanedTextImportResult r) {
        return Map.of("clauses", r.document().clauses(), "chunks", r.chunks(), "elements", r.document().elements(), "quality", r.qualityReport());
    }

    private void writeJson(Path path, Object value) throws Exception {
        Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(value), StandardCharsets.UTF_8);
    }

    private static String rate(long missing, int count) {
        return count == 0 ? "N/A" : String.format(Locale.ROOT, "%.2f%%", 100.0 * (count - missing) / count);
    }
}
