# Phase 5.7 — Legal PDF Index Readiness & Retrieval Report

日期：2026-09-04  
结论：**PARTIAL PASS / BLOCKED for Phase 6**

## 1. Quality Gate

Phase 5.6C persisted baseline was 30 PDFs: PASS 11, REVIEW_REQUIRED 19, FAILED 0. The final gate uses document-level evidence from `PHASE5_6_APPROVED_REINDEX_MANIFEST.json` and current persisted integrity; a warning alone is non-blocking.

Final disposition: PASS 25, REVIEW_REQUIRED 5, REJECTED 0.

The five isolated documents are 安全帽 (unresolved missing expected clauses), 建筑与市政工程地下水控制技术规范 (unresolved coverage), 建筑基坑支护技术规程 (unresolved coverage), 建筑施工碗扣式钢管脚手架安全技术规范 (formula/table-adjacent coverage), and 职业健康监护技术规范 (large unresolved coverage). The other 14 former REVIEW documents were promoted because their evidence showed OCR, numbering, oversized-table/chunk, or false-positive coverage warnings without confirmed body/table/hierarchy loss.

`PHASE5_7_FINAL_INDEX_MANIFEST.json` is the sole indexing basis. The script also synchronizes `quality_status` and `index_eligible` only for the 30 scoped PDF document IDs.

## 2. Final Manifest

- Total PDFs: 30
- Index-eligible PDFs: 25
- Isolated PDFs: 5
- REJECTED PDFs: 0
- Index-eligible chunks: 5,256
- Parser version: `legal-pdf-mineru-adapter/2.0.0`

## 3. Embedding

- Provider: `tei`
- Model: `bge-m3`
- Endpoint: `http://127.0.0.1:18080/v1/embeddings`
- Project target dimension: 1536
- TEI native output: 1024; existing `TeiEmbeddingClient` pads to the configured 1536 target
- Smoke text: `施工现场临边应设置防护栏杆。`
- Direct TEI smoke: PASS, one vector, native dimension 1024, latency about 170 ms
- Application smoke: PASS, provider/model/target dimension 1536, no NaN/Inf and not all-zero
- Indexing: 5,256 successful; failed: 0

The existing `tei-bge-m3` container was restored from the repository's existing local model mount. No model or embedding architecture was changed.

## 4. Vector

| Check | Result |
|---|---:|
| Expected eligible vectors | 5,256 |
| Actual current-batch PDF vectors | 5,256 |
| Missing vector | 0 |
| Duplicate vector | 0 |
| Orphan vector | 0 |
| REVIEW/REJECTED vector leakage in current parser 2.0.0 PDF batch | 0 |

The full collection contains 36,342 vectors because historical Legal KB vectors remain present; they were not counted as current-batch PDF vectors.

## 5. Retrieval Regression

The existing 50-question Gold set was used without modification. This run is Vector-only with the existing bge-m3 model, PgVector Top20, and no BM25/Keyword channel. `rag.keyword.type=none` and keyword retrieval remains disabled.

| Metric | All Gold | Reachable Gold |
|---|---:|---:|
| Recall@1 | 0.00 | 0.00 |
| Recall@3 | 0.02 | 0.02 |
| Recall@5 | 0.02 | 0.02 |
| Recall@10 | 0.02 | 0.02 |
| Recall@20 | 0.02 | 0.02 |
| MRR@10 | 0.0100 | not separately emitted |
| MRR@20 | 0.0100 | not separately emitted |
| nDCG@10 | 0.0126 | not separately emitted |
| Miss@20 | 49 | 49 |

Gold association: total 50, reachable 50, isolated 0, retrieval misses among reachable 49. This is a genuine regression result for the Vector-only diagnostic, not a claim of hybrid/rerank performance. It is not directly comparable to the historical Phase 2B Hybrid + Rerank baseline, which used Bailian embeddings, BM25, RRF and rerank; that baseline reported Recall@5 0.30, Recall@10 0.30, Recall@20 0.30, MRR@20 0.2113, nDCG@10 0.2327, Miss@20 35.

An authenticated application debug endpoint was not used in this run because it requires a login token; the regression tooling calls the same configured TEI and PgVector path directly and records the fixed query/Top20 results. A representative direct query returned the new `《建筑施工高处作业安全技术规范》JGJ 80-2016.pdf` PASS document, confirming that the new PDF vectors are retrievable. Review isolation was verified at the database index boundary with zero current-batch leakage.

## 6. Integrity

| Check | Count |
|---|---:|
| Duplicate Document hash | 0 |
| Duplicate Clause | 0 |
| Duplicate Chunk | 0 |
| Duplicate Vector | 0 |
| Orphan Clause | 0 |
| Orphan Chunk | 0 |
| Orphan Vector | 0 |
| Empty Chunk | 0 |
| Old parserVersion `legal-pdf-mineru-adapter/1.0.0` in scoped PDF batch | 0 |
| Current parserVersion `legal-pdf-mineru-adapter/2.0.0` | 30 PDFs |

## 7. PDFs currently in Legal RAG

**25 / 30 PDF currently indexed and retrievable** at the vector index boundary. The five REVIEW_REQUIRED PDFs are persisted for audit but have no default Legal RAG Vector.

## 8. Known Limitations

- Five review PDFs remain isolated because completeness is not proven by existing evidence.
- OCR Review and oversized table/chunk warnings remain visible as quality diagnostics.
- Keyword/BM25 is intentionally not enabled in the current configuration.
- Vector-only regression is far below the historical hybrid/rerank baseline; the result must not be hidden by changing Gold, queries, TopK, model, or rerank parameters.
- The Gold source set includes documents outside the current 30-PDF replacement batch and historical vectors, so an all-collection comparison is not a like-for-like Phase 5.6C PDF-only benchmark.

## 9. Final Conclusion

Phase 5.7 achieved the quality-gate unification, final manifest, TEI bge-m3 restoration, PASS-only PgVector indexing, and zero-leakage/integrity checks. However, the fixed Vector-only regression has an unexplained severe decline and the full application-level authenticated regression suite was not completed.

**Phase 5.7: PARTIAL PASS / BLOCKED**  
**Ready for Phase 6: NO**

Do not modify the frozen PDF parser, chunk rules, Gold set, query, retriever parameters, prompt, Agent, Citation, or Safe-team to improve this result.
