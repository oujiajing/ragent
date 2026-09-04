# Phase 5.6B Safe Reindex Execution Report

状态：**BLOCKED（未执行删除或重建）**

## 1. Approved Manifest

Phase 5.6A 清单已核验：30 份 PDF，PASS 25、REVIEW_REQUIRED 5、REJECTED 0。

## 2. Reindex 前真实数据库状态

`PRE_REINDEX_STATE.json` 已由运行中的 PostgreSQL 实时读取生成。30/30 文件名和 SHA-256 与 Approved Manifest 精确匹配。

| 范围 | Document | Clause | Chunk | Vector |
|---|---:|---:|---:|---:|
| 本批 30 PDF | 30 | 8,130 | 8,439 | 0 |
| 全部 legal-corpus-2b | 123 | 42,461 | 43,440 | 31,086 |
| Legal TXT | 93 | — | — | — |

实际配置为 PostgreSQL pgvector、集合 `legal_corpus_2b`、embedding model `bge-m3`、dimension 1536、关键词类型 `none`。文档表没有 `index_eligible` 列，实际资格字段位于 Clause/Chunk。

## 3. Backup

备份成功且已验证非空：

- `PHASE5_6B_BACKUP_MANIFEST.json`
- `backups/phase5-6b-20260904T092336Z/ragent-pre-reindex.dump`
- 大小：212,372,808 bytes
- SHA-256：`9a86fc287d01fd5de310c227ed00b04e09b5e4f2d584e8b369a2f4cfbdaf991c`

## 4. Delete Plan

`PHASE5_6B_DELETE_PLAN.json` 为 Dry Run 计划，精确覆盖 30 份 PDF：8,130 Clause、8,439 Chunk、26,255 element、0 vector、0 keyword ref。范围检查通过：未包含 TXT、未包含其他 KB、未包含未列入 Manifest 的文档，所有 hash 匹配。

## 5–8. 实际删除、导入和索引

均未执行。原因是现有生产批处理入口只能调用 MinerU 在线解析，无法消费本阶段要求的缓存 `result.zip`；而冻结的 `LegalDocumentImportAdapter` 固定写入 `legal-pdf-mineru-adapter/1.0.0`，与缓存 manifest 的 `mineru-result/v4` 不一致。绕过入口、启动新解析或修改冻结 Adapter 都会违反本阶段约束。

因此：旧数据仍保留；未生成新 Document/Clause/Chunk/Vector；5 份 REVIEW_REQUIRED 未被写入默认索引，也未宣称已完成隔离验收。

## 9–12. 验收、Embedding 与 Retrieval Regression

未执行。没有新的 post-reindex 数据、embedding 结果或 regression 结果可报告；不得将历史 Phase 2B 指标冒充本阶段结果。`POST_REINDEX_STATE.json` 明确记录 `executionStatus=BLOCKED`。

## 13. 已知限制

需要在不改动冻结解析实现的前提下，提供一个已验收的 cached MinerU result 执行入口，或由用户明确批准相应最小代码变更；同时需要明确新版 Legal PDF parserVersion 的契约。当前关键词后端配置为 `none`，ES 中已有的 `rag_keyword_store` 不属于本次实际启用索引。

## 14. Phase 6 决定

**不允许进入 Phase 6。** Phase 5.6B 为 BLOCKED：备份和删除计划已完成，但安全删除、缓存重导入、Embedding、Review 隔离、完整性验收和 Retrieval Regression 尚未完成。
