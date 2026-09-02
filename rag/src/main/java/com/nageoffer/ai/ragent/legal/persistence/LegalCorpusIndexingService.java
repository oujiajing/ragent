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

package com.nageoffer.ai.ragent.legal.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.chunk.model.EmbeddedChunk;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import com.nageoffer.ai.ragent.rag.core.keyword.KeywordIndexService;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Indexes only eligible persisted legal chunks through the existing embedding/vector/keyword SPIs. */
@Service
@RequiredArgsConstructor
public class LegalCorpusIndexingService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChunkEmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final org.springframework.beans.factory.ObjectProvider<KeywordIndexService> keywordIndexProvider;

    public int eligibleChunkCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM t_knowledge_chunk WHERE kb_id = ? AND index_eligible = TRUE AND deleted = 0",
                Integer.class, LegalCorpusPersistenceService.KB_ID);
    }

    public int indexAll() {
        List<Row> rows = jdbcTemplate.query("""
                SELECT id, doc_id, chunk_index, content, embedding_text, metadata
                FROM t_knowledge_chunk
                WHERE kb_id = ? AND index_eligible = TRUE AND deleted = 0
                ORDER BY doc_id, chunk_index
                """, (rs, n) -> new Row(rs.getString("id"), rs.getString("doc_id"), rs.getInt("chunk_index"),
                rs.getString("content"), rs.getString("embedding_text"), rs.getString("metadata")), LegalCorpusPersistenceService.KB_ID);
        Map<String, List<Row>> byDoc = rows.stream().collect(Collectors.groupingBy(Row::docId, LinkedHashMap::new, Collectors.toList()));
        VectorTarget target = new VectorTarget(LegalCorpusPersistenceService.COLLECTION, "qwen-emb-8b", 1536);
        for (var entry : byDoc.entrySet()) {
            List<Chunk> chunks = entry.getValue().stream().map(this::toChunk).toList();
            List<EmbeddedChunk> embedded = embeddingService.embed(chunks, target);
            vectorStoreService.deleteDocumentVectors(target.partition(), entry.getKey());
            vectorStoreService.indexDocumentChunks(target.partition(), entry.getKey(), embedded);
            KeywordIndexService keyword = keywordIndexProvider.getIfAvailable();
            if (keyword != null) {
                keyword.deleteDocumentIndex(target.partition(), entry.getKey());
                keyword.indexDocumentChunks(target.partition(), entry.getKey(), embedded);
            }
        }
        return rows.size();
    }

    private Chunk toChunk(Row row) {
        Map<String, Object> extras = Map.of();
        try {
            if (row.metadata() != null && !row.metadata().isBlank()) {
                extras = objectMapper.readValue(row.metadata(), new TypeReference<>() {});
            }
        } catch (Exception e) {
            throw new IllegalStateException("Legal chunk metadata JSON 解析失败: " + row.id(), e);
        }
        return new Chunk(row.id(), row.chunkIndex(), row.content(),
                row.embeddingText() == null || row.embeddingText().isBlank() ? row.content() : row.embeddingText(),
                ChunkMetadata.empty().withExtras(extras));
    }

    private record Row(String id, String docId, int chunkIndex, String content, String embeddingText, String metadata) {}
}
