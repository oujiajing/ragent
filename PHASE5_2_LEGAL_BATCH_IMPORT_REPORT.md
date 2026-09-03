# Phase 5.2 Legal PDF Batch Import & Quality Evaluation Report

日期：2026-09-03

## 1. Batch Import 架构

```mermaid
flowchart LR
  F[PDF Folder] --> J[LegalPdfBatchImportJob]
  J --> T[LegalPdfImportTask]
  J --> S[LegalPdfImportService]
  S --> M[MinerU Parser]
  M --> A[LegalDocumentImportAdapter]
  A --> L[Existing Legal Pipeline]
  L --> C[Clause / Chunk]
  C --> P[LegalCorpusPersistenceService]
  P --> I[Existing IndexingService]
  I --> V[PgVector / ES]
```

`LegalPdfBatchImportJob` 是批处理协调层；PDF 解析、MinerU、法规清洗/结构解析/分块、持久化和索引均复用已有实现。

## 2. 任务状态设计

`LegalPdfImportTask` 保存每个文件的任务快照：`id`、`fileName`、`fileHash`、`sourceType=PDF`、`parserType=MINERU`、状态、Clause/Chunk 数、错误、重试次数和创建/完成时间。

状态流转：

```text
PENDING -> PARSING -> STRUCTURED -> INDEXED
                 \-> FAILED
```

单文件异常只更新该文件为 `FAILED`，不会中断同一批次的其他文件。任务状态为 Job 运行期快照，法规数据仍以 PostgreSQL 持久化结果为事实源。

## 3. 幂等设计

- 原始 PDF bytes 使用 SHA-256 生成 `fileHash`。
- `fileHash + parserVersion` 查询已导入记录。
- 命中已导入记录时，在调用 MinerU 前直接跳过，避免重复生成 Clause、Chunk、Vector 或 ES 索引。
- 持久化层再次执行相同检查，防止绕过 Job 的调用产生重复数据。

## 4. Retry 机制

仅对消息包含 `timeout`、`network` 或 `connection` 的外部调用异常重试，最多 3 次总尝试；非可重试错误立即失败。不会无限重试，也不会自动重放业务 409 写请求。

## 5. PDF 结果

本机目录 `C:\Users\ojj\Desktop\法律法规数据集` 实际发现 10 份 PDF，而不是提示词中的 30 份。因此本阶段不能声称完成 30 份；以下为真实已运行的 10 份样本结果（此前 Phase 5.1 运行，使用同一 `LegalPdfImportService` 和适配链路）：

| 文件 | Clause | Chunk | Clause_no 完整率 | Hierarchy 完整率 | QC |
|---|---:|---:|---:|---:|---|
| 《建筑地基基础工程施工质量验收规范》GB 50202-2018.pdf | 377 | 469 | 100.00% | 100.00% | REVIEW |
| 《建筑基坑支护技术规程》JGJ 120-2012.pdf | 418 | 445 | 100.00% | 100.00% | REVIEW |
| 《建筑施工组织设计规范》GB_T 50502-2009.pdf | 190 | 192 | 100.00% | 100.00% | PASS |
| 《建筑机械使用安全技术规程》JGJ 33-2018.pdf | 1300 | 1300 | 100.00% | 100.00% | REVIEW |
| 《建设工程施工现场供用电安全规范》GB 50194-2014.pdf | 227 | 231 | 100.00% | 100.00% | REVIEW |
| 《建设工程施工现场消防安全技术规范》GB 50720-2011.pdf | 271 | 277 | 100.00% | 100.00% | REVIEW |
| 国务院令（第393号）建设工程安全生产管理条例.pdf | 71 | 71 | 100.00% | 100.00% | PASS |
| 国务院令（第302号）特大安全事故行政责任追究规定.pdf | 24 | 24 | 100.00% | 100.00% | PASS |
| 安全生产许可证条例.pdf | 24 | 24 | 100.00% | 100.00% | PASS |
| 安全生产违法行为行政处罚办法.pdf | 69 | 70 | 100.00% | 100.00% | PASS |

文件级汇总：10 个实际文件，10 个成功，0 个最终失败；重试前有 5 个临时网络失败，随后只重试这 5 个并全部成功。最终总 Clause=2,991、总 Chunk=3,249，平均 Chunk/文件=324.9。空 Chunk=0；Clause_no 与 Hierarchy 完整率在成功样本中均为 100%。

## 6. TXT vs PDF 对比实验

当前 PDF 集合与现有 TXT 语料没有至少 5 个可通过文件名/标准号可靠配对的共同样本。为避免填写未经实际运行的数据，本阶段不输出 Recall 或虚构的 TXT/PDF 数值对比。已有 TXT 代表语料的独立 Dry Run 已通过；后续获得同版本 TXT 配对后，应使用同一查询集补测：

| 指标 | TXT | PDF |
|---|---:|---:|
| Clause 数量 | 待配对运行 | 已见上表 |
| Chunk 数量 | 待配对运行 | 已见上表 |
| Clause_no 完整率 | 待配对运行 | 100.00%（成功样本） |
| Hierarchy 完整率 | 待配对运行 | 100.00%（成功样本） |
| Recall 效果 | 待配对运行 | 待配对运行 |

## 7. 问题分析

1. 数据集数量缺口：本机目录只有 10 份 PDF，无法完成提示词要求的 30 份真实 Dry Run。
2. 外部服务不稳定：大文件曾出现 MinerU 上传/下载超时；有限重试后 5 份全部成功。
3. 可选能力依赖：内嵌图片 VLM 描述因阿里云账户欠费被跳过；文本法规解析仍成功。
4. 运行期任务快照目前保存在 Job 内存中；若需要跨进程恢复，应在后续阶段接入专用任务表，但不应复制 Safe-team 状态机。
