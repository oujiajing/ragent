# Phase 5.8 — Embedding Space Consistency & Retrieval Recovery Report

日期：2026-09-04
结论：**PARTIAL PASS / BLOCKED**
Ready for Phase 6：**NO**

## 1. Phase 5.7 严重回归根因

Phase 5.7 的 collection 同时包含 5,256 个新版 TEI/bge-m3 PDF Vector 与约 31,086 个历史 Legal Vector。历史 Vector 的数据库 metadata 没有 provider/model 字段，但由 Phase 2B 报告、历史配置与索引日志确认其原始空间为 Bailian `text-embedding-v4`/1536；新版 PDF 使用 TEI `bge-m3`，native 1024 后由既有应用逻辑 zero-pad 到 1536。

因此 Phase 5.7 的 full-collection cosine search 确实存在混合 embedding space 污染。但 current-PDF-only 实验 Recall@20 也只有 0.05，说明混合空间不是唯一根因；不能把回归完全归因于污染。

## 2. Vector Provenance

`PHASE5_8_VECTOR_PROVENANCE_AUDIT.json`：

- totalVectors：36,342
- historical source vectors：31,086
- current PDF source vectors：5,256
- final current bge-m3 vectors：36,342
- unknown provenance metadata vectors：31,086（模型已由重建过程确定，但原始 Vector 行没有显式 provider/model）
- all final Vector dimensions：1536

现有 Vector metadata 记录 doc/chunk/条款信息，但不记录 `embeddingProvider`、`embeddingModel`、`embeddingDimension`、`createdAt`。这是 provenance 可追溯性的已知缺口。

## 3. Historical Vector 实际模型

历史 Vector 原始模型为：Bailian / `text-embedding-v4` / 1536。证据来自历史 Phase 2B 报告、`bootstrap/src/main/resources/application.yaml` 的 Bailian embedding candidate、以及历史 indexing 日志。对该 provider 的真实 smoke 返回 HTTP 400，因此没有选择 Strategy A。

## 4. 当前 Query 实际模型

Gold query 逐条通过真实 `http://127.0.0.1:18080/v1/embeddings` 请求生成：

- provider：TEI
- model：bge-m3
- native dimension：1024
- application dimension：1536
- deterministic same-prefix：true
- zero-padded tail 512：true

同一文本的 document/query transform 一致，未发现 padding 实现不一致。

## 5. Current-only vs Full-collection

固定 50 条 Gold、原 query、原 bge-m3、PgVector Top20，Keyword/BM25 仍关闭：

| Scope | Gold | Recall@20 | Miss@20 |
|---|---:|---:|---:|
| Current bge-m3 PDF only | 20 current-PDF Gold | 0.05 | 19 |
| Full unified collection | 50 | 0.02 | 49 |

Current-PDF-only Top20 的代表性结果中，20 条 current-PDF Gold 仅 1 条命中；因此 Test A 本身并不正常。Test B 的异常不能单独证明由混合空间造成。

## 6. Strategy 选择与执行

选择 **Strategy B：全 Legal KB 迁移到 bge-m3**。

原因：

1. 历史 Bailian provider 真实 smoke 不可用；
2. 不应在同一 collection 中长期混用 Bailian 与 bge-m3；
3. 现有 Legal KB 有 36,342 个 eligible Chunk，可通过既有 `LegalCorpusIndexingService` 全量重建；
4. 未修改 Gold、Query、Retriever 参数、Rerank、Prompt 或 Agent。

执行结果：

- 全量重建 scope：all eligible Legal
- Vector 重建数量：36,342
- 最终统一模型：TEI / bge-m3 / native 1024 → application 1536 zero-padding
- final Vector count：36,342

## 7. Retrieval Regression

全量 bge-m3 重建后重新执行固定 Gold：

| Scope | Recall@1 | Recall@3 | Recall@5 | Recall@10 | Recall@20 | MRR@10 | MRR@20 | nDCG@10 | Miss@20 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Current PDF | 0.00 | 0.00 | 0.05 | 0.05 | 0.05 | 0.0100 | 0.0100 | 0.0193 | 19/20 |
| Full collection | 0.00 | 0.02 | 0.02 | 0.02 | 0.02 | 0.0100 | 0.0100 | 0.0126 | 49/50 |

Gold association：原始 Gold 50 条；Current PDF Gold 20 条；isolated PDF Gold 0 条；historical non-PDF Gold 30 条。Current PDF Gold 没有因 Review PDF 被隔离而丢失；19 条是 current-only retrieval miss。

Phase 5.7 对比：Recall@20=0.02、Miss@20=49/50。Phase 5.8 统一后 Full collection 仍为 Recall@20=0.02、Miss@20=49/50，未观察到可接受恢复。因此不能将 Phase 5.8 判为 PASS。

## 8. Integrity

- duplicate Vector：0
- orphan Vector：0
- missing Vector：0
- all final Vector dimensions：1536
- current Review PDF Vector leakage：0
- current rejected PDF Vector leakage：0
- final mixed embedding space in default collection：false（全量参与检索的 Vector 已由同一 bge-m3 流程重建）
- duplicate Document/Clause/Chunk：0
- orphan Clause/Chunk：0
- empty Chunk：0

## 9. 当前真正可检索 PDF 数量

25 / 30 PDF 通过最终 Quality Gate 并进入默认 Legal RAG。5 份 REVIEW_REQUIRED PDF 仍保持隔离、没有 Vector。

## 10. 已知限制

- Full collection 仍保留历史来源文档，但其 Vector 已统一重建为 bge-m3；原始 provenance 仅能通过批次证据推断。
- Current-PDF-only 与 Full collection 均未恢复到可接受 Recall@20；问题可能涉及 chunk/document-to-Gold 对齐、候选排序或语料覆盖，但本阶段不修改 Retriever 或 Gold。
- Keyword/BM25 未启用。
- Bailian Strategy A 未执行，因为真实 provider smoke 失败。

## 11. 最终结论

Phase 5.8 已完成 embedding provenance 审计、Query transform 验证、current-only/full 对照实验，并将全 Legal KB 统一迁移到 bge-m3。混合 embedding space 已消除，但 Retrieval Regression 没有从 0.02 级严重退化中恢复。

**Phase 5.8：PARTIAL PASS / BLOCKED**
**Ready for Phase 6：NO**

停止边界：不继续修改 PDF Parser、Chunker、Retriever 参数、Rerank、Gold、Prompt、Agent 或 Safe-team；Phase 6 不启动。
