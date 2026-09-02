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

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.rerank.RerankService;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordRetrieverService;
import com.nageoffer.ai.ragent.rag.core.retrieval.RetrieveRequest;
import com.nageoffer.ai.ragent.rag.core.vector.VectorRetrieverService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Developer-only retrieval inspection endpoint. It never performs business writes. */
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "ragent.eval", name = "enabled", havingValue = "true")
public class LegalRetrievalDebugController {

    private static final String COLLECTION = "legal_corpus_2b";
    private static final int TOP_K = 20;

    private final VectorRetrieverService vectorRetriever;
    private final ObjectProvider<KeywordRetrieverService> keywordProvider;
    private final RerankService rerankService;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/rag/eval/legal-debug")
    public Map<String, Object> debug(@RequestParam String question) {
        List<RetrievedChunk> vector = vectorRetriever.retrieve(RetrieveRequest.builder()
                .query(question).topK(TOP_K).collectionName(COLLECTION).build());
        KeywordRetrieverService keywordRetriever = keywordProvider.getIfAvailable();
        List<RetrievedChunk> keyword = keywordRetriever == null
                ? List.of() : keywordRetriever.search(question, List.of(COLLECTION), TOP_K);
        List<RetrievedChunk> hybrid = rrf(vector, keyword);
        List<RetrievedChunk> rerankCandidates = new ArrayList<>(hybrid.subList(0, Math.min(TOP_K, hybrid.size())));
        List<RetrievedChunk> reranked = rerankCandidates.isEmpty()
                ? List.of() : rerankService.rerank(question, rerankCandidates, 5);
        return Map.of(
                "query", question,
                "vector", enrich(vector),
                "bm25", enrich(keyword),
                "hybridRrf", enrich(hybrid),
                "hybridRerank", enrich(reranked));
    }

    private List<RetrievedChunk> rrf(List<RetrievedChunk> vector, List<RetrievedChunk> keyword) {
        Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();
        Map<String, Float> scores = new LinkedHashMap<>();
        addRrf(vector, chunks, scores);
        addRrf(keyword, chunks, scores);
        chunks.forEach((id, chunk) -> chunk.setScore(scores.get(id)));
        return chunks.values().stream().sorted(RetrievedChunk.BY_SCORE_DESC).limit(TOP_K).toList();
    }

    private void addRrf(List<RetrievedChunk> input, Map<String, RetrievedChunk> chunks, Map<String, Float> scores) {
        for (int i = 0; i < input.size(); i++) {
            RetrievedChunk chunk = input.get(i);
            chunks.putIfAbsent(chunk.getId(), chunk);
            scores.merge(chunk.getId(), 1f / (60 + i + 1), Float::sum);
        }
    }

    private List<Map<String, Object>> enrich(List<RetrievedChunk> chunks) {
        return chunks.stream().map(c -> jdbcTemplate.queryForObject("""
                SELECT d.doc_title, d.standard_no, c.clause_no, c.hierarchy_path, c.content
                FROM t_knowledge_chunk c JOIN t_knowledge_document d ON d.id=c.doc_id
                WHERE c.id=?
                """, (rs, n) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("chunkId", c.getId());
                    result.put("score", c.getScore());
                    result.put("rerankScore", c.getRerankScore());
                    result.put("docTitle", rs.getString("doc_title"));
                    result.put("standardNo", rs.getString("standard_no"));
                    result.put("clauseNo", rs.getString("clause_no"));
                    result.put("hierarchyPath", rs.getString("hierarchy_path"));
                    result.put("preview", preview(rs.getString("content")));
                    return result;
                }, c.getId())).toList();
    }

    private String preview(String content) {
        if (content == null) return "";
        return content.length() <= 180 ? content : content.substring(0, 180) + "…";
    }
}
