# Phase 5.9 — Latest 30 PDF Clean Rebuild and PDF-only Evaluation

日期：2026-09-04

结论：**PARTIAL PASS**

## 1. 独立 PDF 数据源

- 原件目录：`C:\Users\ojj\Desktop\法律法规数据集`
- PDF：30
- 其他文件/TXT：0
- 缓存目录：`.output/legal-pdf-cache`
- 文件名与缓存清单：30/30 一致
- 原件 SHA-256 与缓存 manifest `pdfSha256`：30/30 一致
- 最终数据库 `fileHash` 与原件 SHA-256：30/30 一致

缓存 `origin.pdf` 与原件 30/30 字节不同，因此最终入库明确读取独立目录中的 PDF 原件，仅复用同名缓存 `result.zip`。没有把缓存副本冒充最新原件。

## 2. 删除与备份

执行前创建 PostgreSQL custom-format 备份：

- 文件：`backups/phase5-9-20260904T211049/ragent-pre-pdf-rebuild.dump`
- 大小：238,924,390 bytes
- SHA-256：`9b65bdabe8ef3ff49a60d24343f0d3a7a8e22a1dcb1297145b385d67667b2624`
- `pg_restore -l`：通过

删除范围只包含 Legal KB 的全部旧 PDF：30 Document、6,788 Clause、7,064 Chunk、5,256 Vector。93 个历史 TXT Document 保留，Safe-team 与其他知识库未修改。

## 3. 最新 PDF 入库结果

- processed/persisted：30/30
- failed：0
- parserVersion：`legal-pdf-mineru-adapter/2.0.0`
- parsed text：1,055,997 chars
- tables：250
- clauses：6,788
- chunks：7,064
- empty chunks：0
- unstructured paragraphs：0
- oversized chunks：46（保留为质量告警）

本次仅调整批处理编排，使 PDF 字节来自独立原件目录；Parser、LegalSectionFilter、Cleaning、StructureParser 和 Chunker 均未修改。

## 4. Quality Gate 与 Vector

- PASS：25 PDF / 5,256 Chunk
- REVIEW_REQUIRED：5 PDF / 1,808 Chunk
- REJECTED：0
- PASS Vector：5,256 / 5,256
- REVIEW Vector leakage：0
- embedding：TEI / bge-m3 / native 1024 → application 1536 zero-padding

5 份 REVIEW_REQUIRED 文档仍保留在数据库供审计，但不参与默认 PDF 检索。

## 5. 数据完整性

| Check | Count |
|---|---:|
| Duplicate PDF hash | 0 |
| Duplicate Clause | 0 |
| Duplicate Chunk | 0 |
| Duplicate Vector | 0 |
| Orphan Clause | 0 |
| Orphan Chunk | 0 |
| Orphan Vector | 0 |
| Empty PDF Chunk | 0 |
| Missing PASS Vector | 0 |
| Review PDF Vector leakage | 0 |
| Old PDF parserVersion | 0 |

## 6. 30 PDF 专用评测集

生成 `PHASE5_9_30PDF_GOLD.json`：

- 来源文档：30 PDF，覆盖率 30/30
- 每份 PDF 两条：1 条结构/条款查询 + 1 条正文内容查询
- 总用例：60
- PASS PDF 可检索用例：50
- REVIEW PDF 隔离用例：10
- 历史 TXT 来源用例：0

每个 Gold 都保存 filename、fileHash、documentId、goldChunkId、clauseNo、hierarchyPath 和 evidence preview。数据集由本次真实持久化 Chunk 确定性生成，没有复用原 Phase 2B TXT Gold。

## 7. PDF-only 检索范围

评测 SQL 强制限定：

- `file_type='pdf'`
- `parser_version='legal-pdf-mineru-adapter/2.0.0'`
- `quality_status='PASS'`
- Top20 不变

历史 TXT Vector 即使仍存在于 Legal collection，也不会进入本次候选或指标。

## 8. Retrieval Evaluation

### Exact Chunk

| Metric | Result |
|---|---:|
| Recall@1 | 0.02 |
| Recall@3 | 0.02 |
| Recall@5 | 0.06 |
| Recall@10 | 0.16 |
| Recall@20 | 0.20 |
| MRR@10 | 0.0402 |
| MRR@20 | 0.0429 |
| nDCG@10 | 0.0666 |
| Miss@20 | 40 / 50 |

### Document-level

| Metric | Result |
|---|---:|
| Recall@1 | 0.52 |
| Recall@3 | 0.66 |
| Recall@5 | 0.74 |
| Recall@10 | 0.82 |
| Recall@20 | 0.82 |
| MRR@10 | 0.6134 |
| MRR@20 | 0.6134 |
| nDCG@10 | 0.6630 |
| Miss@20 | 9 / 50 |

按查询类型，Exact Chunk Recall@20：结构查询 0.16，内容查询 0.24。

## 9. 结果解释

最新 PDF 的入库、解析、清洗、分块、Quality Gate、Embedding 和隔离链路工作正常。PDF-only 文档级检索已经可以较稳定地定位来源 PDF，Recall@5 为 0.74、Recall@20 为 0.82。

但条款级精确定位明显不足：Exact Chunk Recall@20 仅 0.20。多数失败不是“召回到 TXT”，而是在同一批 PDF 的其他 Chunk 中排序靠前，或者只命中了正确文档的其他条款。因此当前可以证明 PDF 文档级检索有效，不能证明 Clause/Chunk 级定位已达到可接受质量。

## 10. 限制与结论

- 本评测集是从真实 Chunk 确定性生成的结构查询和内容 probe，适合流水线验收，但不是第二位人工编写的自然语言 Gold。
- Keyword/BM25 未启用，本次为 Vector-only。
- 5 份 REVIEW PDF 被主动隔离，其 10 个用例不计入可检索 Recall。
- 未修改 Gold 结果、Retriever 参数、Rerank、Prompt 或 Parser 来提升指标。

**Phase 5.9：PARTIAL PASS**

- PDF ingestion / parsing / cleaning / chunking：PASS
- PDF document-level retrieval：PASS WITH LIMITATIONS
- Exact Clause/Chunk retrieval：REVIEW_REQUIRED
- Ready for Phase 6：NO

后续若继续，应只诊断 Chunk embedding text 与 Gold chunk 对齐/同文档 Chunk 排序问题；不要重新混入 TXT 评测，也不要通过修改 TopK 或删除 miss 用例提升结果。
