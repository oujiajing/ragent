# Phase 5.6C — 30 PDF Production Reimport Report

日期：2026-09-04 ；结果：**PARTIAL PASS**

## 旧库与删除

备份文件已复核存在、非空且 SHA-256 匹配：`9a86fc287d01fd5de310c227ed00b04e09b5e4f2d584e8b369a2f4cfbdaf991c`。
删除前本批为 Document 30、Clause 8,130、Chunk 8,439、Vector 0、Keyword 0。已按 30 个 PDF hash 删除旧 PDF Document、Element、Clause、Quality、Chunk 和其目标向量；TXT、其他知识库、Agent、Hazard、Trace 和 Safe-team 数据未纳入删除范围。

## 新版导入

- processed：30
- persisted：30
- PASS：11
- REVIEW_REQUIRED：19（数据库兼容字段为 `REVIEW`）
- FAILED：0
- parserVersion：`legal-pdf-mineru-adapter/2.0.0`（旧值 `legal-pdf-mineru-adapter/1.0.0` 已清零）
- Cached MinerU：30/30 使用已有 `result.zip`，未新建 MinerU 任务。

| PDF | 状态 | Clause | Chunk | indexEligible |
|---|---:|---:|---:|---:|
| 国务院令302号 | PASS | 24 | 24 | true |
| 国务院令393号 | PASS | 71 | 71 | true |
| 土方与爆破工程施工及验收规范 | REVIEW_REQUIRED | 271 | 271 | false |
| 安全帽 | REVIEW_REQUIRED | 23 | 30 | false |
| 安全生产许可证条例 | PASS | 24 | 24 | true |
| 安全生产违法行为行政处罚办法 | PASS | 69 | 70 | true |
| 建筑与市政工程地下水控制技术规范 | REVIEW_REQUIRED | 200 | 209 | false |
| 建筑与市政工程施工现场临时用电安全技术标准 | REVIEW_REQUIRED | 243 | 245 | false |
| 建筑地基基础工程施工质量验收规范 | REVIEW_REQUIRED | 280 | 368 | false |
| 建筑基坑支护技术规程 | REVIEW_REQUIRED | 340 | 366 | false |
| 建筑施工安全技术统一规范 | PASS | 96 | 97 | true |
| 建筑施工易发事故防治安全标准 | PASS | 285 | 286 | true |
| 建筑施工碗扣式钢管脚手架安全技术规范 | REVIEW_REQUIRED | 187 | 195 | false |
| 建筑施工组织设计规范 | PASS | 98 | 98 | true |
| 建筑施工脚手架安全技术统一标准 | PASS | 174 | 183 | true |
| 建筑施工起重吊装工程安全技术规范 | REVIEW_REQUIRED | 142 | 148 | false |
| 建筑施工门式钢管脚手架安全技术标准 | REVIEW_REQUIRED | 227 | 243 | false |
| 建筑施工高处作业安全技术规范 | PASS | 111 | 111 | true |
| 建筑机械使用安全技术规程 | REVIEW_REQUIRED | 1,195 | 1,195 | false |
| 建筑灭火器配置设计规范 | PASS | 122 | 122 | true |
| 建筑行业职业病危害预防控制规范 | REVIEW_REQUIRED | 97 | 99 | false |
| 建筑设计防火规范 | REVIEW_REQUIRED | 225 | 233 | false |
| 建设工程施工现场供用电安全规范 | REVIEW_REQUIRED | 203 | 205 | false |
| 建设工程施工现场消防安全技术规范 | REVIEW_REQUIRED | 225 | 233 | false |
| 建设工程监理规范 | REVIEW_REQUIRED | 189 | 189 | false |
| 施工企业安全生产评价标准 | PASS | 51 | 51 | true |
| 施工现场机械设备检查技术规范 | REVIEW_REQUIRED | 516 | 517 | false |
| 爆破安全规程 | REVIEW_REQUIRED | 96 | 170 | false |
| 石棉作业职业卫生管理规范 | REVIEW_REQUIRED | 3 | 3 | false |
| 职业健康监护技术规范 | REVIEW_REQUIRED | 1,001 | 1,008 | false |

## 新知识库与质量

全库当前为 Document 123、Clause 41,119、Chunk 42,065、Vector 31,086；本批为 Clause 6,788、Chunk 7,064、Vector 0、Keyword 0。当前 Keyword 配置为 `none`，因此没有实际 Keyword/ES 写入。PASS Chunk 数 1,137，Review Chunk 数 5,927，Review 文档未进入默认索引。

质量检查：空 Chunk 0；目标 PDF 向量 0；当前导入结果中的 OCR/结构/超长不确定性触发 REVIEW_REQUIRED。非正文噪声沿用现有 Phase 5.4/5.5 规则，未发现新增明确污染证据。VLM 描述在本次回放中关闭；图片描述欠费告警未阻断文本导入。

## Integrity / idempotency

duplicate Document/hash 0；duplicate Clause 0；duplicate Chunk 0；orphan Clause 0；orphan Chunk 0；orphan Vector 0；empty Chunk 0。当前索引失败前没有目标 PDF Vector 或 Keyword 残留。重复导入逻辑仍按 `fileHash + parserVersion` 返回 `ALREADY_IMPORTED`；本阶段未再次执行破坏性重复导入，以免重置已完成批次。

## Embedding / Retrieval Regression

PASS 文档索引执行被运行环境阻断：`provider=tei, modelId=bge-m3` 的 embedding client 未配置/不可用，返回 `All Embedding model candidates failed`。因此本批正式进入默认 Legal RAG 的 PDF 数量为 **0**，不能声称完成 Vector、Hybrid 或 Rerank regression；Gold 数据、query、retrieval 和 rerank 参数均未修改。现有历史全库向量 31,086 未被冒充为本批结果。

## 最终结论

旧版 30 份 PDF 已完整替换为新 parserVersion，30/30 完成缓存重导尝试，无 FAILED；11 份 PASS 已持久化且具备 eligible Chunk，19 份 REVIEW_REQUIRED 已真实隔离。由于 embedding provider 不可用，尚无 PDF 真正进入默认 Legal RAG，Retrieval Regression 未完成。

结论：**PARTIAL PASS，不建议进入 Phase 6**。先提供并验证 bge-m3/TEI embedding provider，随后仅对 11 份 PASS 文档建 Vector 并运行 Gold regression；不得强行索引 19 份 Review 文档。
