# Phase 5.6 Safe Reindex Report

状态：**NOT PASS / BLOCKED**

## 结论

本次未执行删除、重建、Embedding、PgVector 或关键词索引操作。原因是执行前置条件不满足：Phase 5.5 全量报告明确写明 `Reindex allowed: NO`，且当前主机没有运行中的 Docker/PostgreSQL 服务，无法完成合规备份、真实旧库计数和回滚验证。

本报告对应的证据化清单为 `PHASE5_6_REINDEX_MANIFEST.json`。状态仅由 Phase 5.5 结构化结果映射得到：`PASS_SAMPLED_CHECKS -> PASS`，`REVIEW_REQUIRED -> REVIEW_REQUIRED`；没有证据支持 `REJECTED`。

## 旧知识库统计

| 项目 | 结果 |
|---|---:|
| 旧 Document | 未读取：PostgreSQL 未运行 |
| 旧 Clause | 未读取 |
| 旧 Chunk | 未读取 |
| 旧 Vector | 未读取 |
| 旧 keyword index | 未读取；当前配置文档显示默认 `rag.keyword.type=none` |
| 既有 Phase 2B 记录的 PDF 基线 | 30 documents / 8,130 clauses / 8,439 chunks |

未生成数据库 dump，因此不存在可宣称的本次 rollback artifact；既有历史备份不作为本次删除前备份。

## Manifest 与预期放行统计

| 项目 | 数量 |
|---|---:|
| PDF 总数 | $($manifestItems.Count) |
| PASS 文档 | $pass |
| REVIEW_REQUIRED 文档 | $review |
| REJECTED 文档 | 0 |
| 预期 PASS Clause | $passClauses |
| 预期 PASS Chunk | $passChunks |
| 预期隔离 Clause | $reviewClauses |
| 预期隔离 Chunk | $reviewChunks |
| 实际重新入库文档 | 0（未执行） |
| 实际 Clause / Chunk / Vector / keyword index | 0 / 0 / 0 / 0（未执行） |

## 被隔离文档

$reviewDocs

隔离原因来自 Phase 5.5 的正文覆盖、超长 Chunk、OCR、缺失 Clause 或层级候选告警；不得通过文件名或人为调整指标放行。

## parserVersion 与幂等

- 30 份缓存记录的 parserVersion 均为 `mineru-result/v4`。
- 现有实现按 `fileHash + parserVersion` 查询 `ALREADY_IMPORTED`。
- 本次未修改 TXT 幂等语义，也未执行导入；因此新版 PDF Pipeline 的数据库重建幂等未完成实证验收。

## 备份、替换与完整性验收

| 检查项 | 结果 |
|---|---|
| 删除前数据库备份 | BLOCKED：PostgreSQL/Docker 未运行 |
| 仅定位 30 份 PDF Legal 数据 | 未执行 |
| 旧/新 Chunk 共存 | 未验证 |
| orphan Clause / Chunk / Vector | 未验证 |
| duplicate Document / Chunk | 未验证 |
| fileHash / parserVersion / indexEligible | Manifest 已生成；数据库未验证 |
| REVIEW_REQUIRED 默认检索隔离 | 未验证 |
| 目录、前言、引用标准名录、本标准用词说明、附录污染检查 | 仅有 Phase 5.5 合成边界证据；未做数据库重建后抽查 |

## Retrieval regression

未执行：没有可用运行时、数据库、Embedding 或索引。以下为既有 Phase 2B 基线，不能当作 Phase 5.6 结果：

| Method | Recall@5 | Recall@10 | Recall@20 | MRR@10 | MRR@20 | nDCG@10 | Miss@20 |
|---|---:|---:|---:|---:|---:|---:|---:|
| Vector | 0.26 | 0.36 | 0.42 | 0.1743 | 0.1792 | — | 29 |
| BM25 | 0.20 | 0.26 | 0.42 | 0.1387 | 0.1481 | — | 29 |
| Hybrid RRF | 0.28 | 0.38 | 0.42 | 0.1761 | 0.1787 | 0.2239 | 29 |
| Hybrid + Rerank Top5 | 0.30 | — | — | 0.2113 | — | 0.2327 | — |

Rerank 仅报告 Top5；不能与 Top20 Recall 等价比较。Review 文档相关问题的默认检索隔离也未执行。

## 已知限制与 Phase 6 决定

- 仍需恢复带 pgvector 的 PostgreSQL、Embedding 服务和（如启用）Elasticsearch/BM25 服务。
- 需要先生成本次删除前数据库备份，再按 manifest 精确删除/重建 30 份 Legal PDF 数据。
- 需要补齐实际新库统计、孤儿/重复检查、幂等复跑、Review 隔离查询和 50 条 Gold regression。
- **不允许进入 Phase 6。** 本次 Phase 5.6 未通过，满足停止条件：无法安全删除/备份且无法验证质量门禁和检索结果。
