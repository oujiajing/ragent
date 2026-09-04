package com.nageoffer.ai.ragent.legal.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.core.chunk.model.Chunk;
import com.nageoffer.ai.ragent.core.chunk.model.ChunkMetadata;
import com.nageoffer.ai.ragent.core.ingest.VectorTarget;
import com.nageoffer.ai.ragent.core.ingest.embed.ChunkEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Opt-in Phase 5.7 smoke gate and manifest-scoped legal PDF indexer. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "rag.legal.phase5-7", name = "index-enabled", havingValue = "true")
public class LegalPhase57Runner implements CommandLineRunner {
    private static final String COLLECTION = LegalCorpusPersistenceService.COLLECTION;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ChunkEmbeddingService embeddingService;
    private final LegalCorpusIndexingService indexingService;

    @Override
    public void run(String... args) throws Exception {
        Path manifestPath = Path.of(System.getProperty("rag.legal.phase5-7.manifest",
                "PHASE5_7_FINAL_INDEX_MANIFEST.json")).toAbsolutePath().normalize();
        String manifestText = Files.readString(manifestPath);
        if (!manifestText.isEmpty() && manifestText.charAt(0) == '\ufeff') manifestText = manifestText.substring(1);
        JsonNode manifest = objectMapper.readTree(manifestText);
        Set<String> ids = new HashSet<>();
        manifest.path("documents").forEach(d -> {
            if (d.path("indexEligible").asBoolean(false)) ids.add(d.path("documentId").asText());
        });
        if (ids.isEmpty()) throw new IllegalStateException("Phase 5.7 manifest has no eligible documents");
        smokeTest(ids.iterator().next());
        boolean full = Boolean.parseBoolean(System.getProperty("rag.legal.phase5-8.full-reindex", "false"));
        int indexed = full ? indexingService.indexAll() : indexingService.indexDocuments(ids);
        log.info("Phase 5 indexing completed: scope={}, documents={}, chunks={}", full ? "all-eligible-legal" : "manifest-pass-pdf", full ? "all" : ids.size(), indexed);
    }

    private void smokeTest(String documentId) {
        String[] row = jdbcTemplate.queryForObject("""
                SELECT id, content, embedding_text FROM t_knowledge_chunk
                WHERE doc_id=? AND index_eligible=TRUE AND deleted=0 ORDER BY chunk_index LIMIT 1
                """, (rs, n) -> new String[]{rs.getString("id"), rs.getString("content"), rs.getString("embedding_text")}, documentId);
        String text = "施工现场临边应设置防护栏杆。";
        Chunk probe = new Chunk("phase57-smoke", 0, text, text, ChunkMetadata.empty());
        var vector = embeddingService.embed(java.util.List.of(probe), new VectorTarget(COLLECTION, "bge-m3", 1536)).get(0).embedding();
        if (vector.length != 1536) throw new IllegalStateException("Phase 5.7 smoke dimension mismatch: " + vector.length);
        boolean nonZero = false;
        for (float value : vector) {
            if (Float.isNaN(value) || Float.isInfinite(value)) throw new IllegalStateException("Phase 5.7 smoke contains NaN/Inf");
            if (value != 0F) nonZero = true;
        }
        if (!nonZero) throw new IllegalStateException("Phase 5.7 smoke returned an all-zero vector");
        log.info("Phase 5.7 embedding smoke test passed: provider=tei, model=bge-m3, dimension=1536, probeChunk={}", row[0]);
    }
}
