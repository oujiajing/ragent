# Phase 5.1 Legal PDF Import Report

日期：2026-09-03
仓库：`ragent` / `feat/safeguard-agent`

## 1. ragent 已有 Document Pipeline 分析

```mermaid
flowchart LR
    U[现有 PDF 上传入口] --> R[ParserRegistry]
    R --> M[MinerUDocumentParser]
    M --> Z[MinerUResultUnpacker]
    Z --> P[ParsedDocument / Block]
    P --> C[通用 ChunkingService]
    C --> E[Embedding]
    E --> I[PgVector / Elasticsearch]
```

已确认的复用点：

- `KnowledgeDocumentController` / `KnowledgeDocumentService` 已提供通用文件上传。
- `ParserRegistry` 按 MIME 与解析档位选择 `MinerUDocumentParser`。
- MinerU 本地字节上传、轮询、ZIP 下载和 Markdown AST 解包均已实现。
- 输出契约是 `ParsedDocument(blocks, metadata)`，Block 包含标题、段落、表格、列表、图片等。
- `DefaultIngestionKernel` 的通用链路是 parse → chunk → embed → index。
- 法规 TXT 链路已有 `LegalCleaningPipeline → LegalStructureParser → LegalChunker → LegalQualityService`。

## 2. MinerU 调用流程

本阶段新增 `LegalPdfImportService`，只编排已有 MinerU Parser 和法规适配器：

```text
PDF bytes
  -> MinerUDocumentParser.parseStructured(application/pdf)
  -> ParsedDocument blocks
  -> LegalDocumentImportAdapter
  -> canonical legal text
  -> existing LegalCleaningPipeline
  -> existing LegalStructureParser
  -> existing LegalChunker
  -> existing LegalQualityService
  -> DRY_RUN result
```

MinerU 真实调用要求 `MINERU_API_KEY`、Redis/RustFS 等运行依赖。本机检查结果：数据集存在 10 份 PDF，但 `MINERU_API_KEY` 未配置，因此本次未声称完成云端 PDF 解析。

## 3. Legal Adapter 设计

新增 `LegalDocumentImportAdapter`：

- 只接受通用 `ParsedDocument`，不重新实现 PDF Parser 或 MinerU。
- 对 Heading、Paragraph、Table、HTML Table、List、Code、Image 做确定性文本渲染。
- 使用原始 PDF bytes 计算 `fileHash`，不使用 MinerU Markdown 替代原始身份。
- 写入 `sourceFormat=MINERU_PDF`、`parserVersion=legal-pdf-mineru-adapter/1.0.0`、原始文件名和 hash。
- 通过 `CleanedTextImporter.importCanonicalText` 进入既有法规结构化与分块链路，因此 TXT/PDF 共用 Clause、Chunk 和 metadata 逻辑。

## 4. PDF 导入流程与测试

已实现：

- PDF 编排服务：`LegalPdfImportService.dryRun`。
- PDF Block 到法规文本适配器。
- 空 MinerU 结果失败处理。
- MinerU 异常原样传播，不降级为未经确认的直接分块。
- TXT 入口保持兼容。

聚焦测试：

- `CleanedTextImporterTest`：6/6 通过。
- `LegalDocumentImportAdapterTest`：2/2 通过。
- `LegalPdfImportServiceTest`：覆盖 MinerU 成功编排和失败传播。
- `LegalCorpusPersistenceIdempotencyTest`：1/1 通过。
- 既有 `LegalCorpusDryRunTest`：使用现有 Phase 2B TXT 样本 1/1 通过。

重复导入的正式幂等仍由现有 `LegalCorpusPersistenceService.findImported(fileHash, parserVersion)` 负责；本阶段 Dry Run 不执行数据库写入，因此不会新增数据。

## 5. TXT/PDF 对比

本机 PDF 数据集清单为 10 份，已确认可作为 Phase 5.1 样本：

1. 《建设工程施工现场供用电安全规范》GB 50194-2014.pdf
2. 《建设工程施工现场消防安全技术规范》GB 50720-2011.pdf
3. 《建筑地基基础工程施工质量验收规范》GB 50202-2018.pdf
4. 《建筑机械使用安全技术规程》JGJ 33-2018.pdf
5. 《建筑基坑支护技术规程》JGJ 120-2012.pdf
6. 《建筑施工组织设计规范》GB_T 50502-2009.pdf
7. 安全生产违法行为行政处罚办法.pdf
8. 安全生产许可证条例.pdf
9. 建设工程安全生产管理条例（国务院公报版本）.pdf
10. 国务院关于特大安全事故行政责任追究的规定（国务院公报版本）.pdf

由于 MinerU API key 缺失，本次未生成 PDF 侧 Clause/Chunk 数量、Clause_no 完整率、Hierarchy 完整率及检索效果的虚构数据。待配置真实 MinerU 环境后，可对同法规 TXT/PDF 结果按以下字段比较：

| 指标 | TXT | PDF/MinerU | 差异 |
|---|---:|---:|---:|
| Clause 数量 | 待运行 | 待运行 | 待运行 |
| Chunk 数量 | 待运行 | 待运行 | 待运行 |
| Clause_no 完整率 | 待运行 | 待运行 | 待运行 |
| Hierarchy 完整率 | 待运行 | 待运行 | 待运行 |
| 检索效果 | 待运行 | 待运行 | 待运行 |

## 6. 问题分析

- 外部阻塞：本机未配置 `MINERU_API_KEY`，无法完成真实 SaaS 解析与 10 份 PDF 的端到端 Dry Run。
- 解析风险：PDF 页眉页脚、扫描件 OCR、表格和条文说明可能改变法规行边界，应在真实结果中抽查 Clause/raw_text/children/hierarchy。
- 设计边界：本阶段没有接入前端，也没有修改 Safe-team、Agent、Citation、Retrieval 或 Legal Chunker 核心逻辑。
