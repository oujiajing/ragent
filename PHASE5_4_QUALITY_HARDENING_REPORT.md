# Phase 5.4 Legal PDF Quality Hardening

完成日期：2026-09-04。范围：3 份既有 MinerU 缓存的 PDF → Legal Chunk 离线加固。

**本阶段验收通过：正文误删=0，目录残留=0，已识别数字条号的章/节归属冲突=0，正文表格覆盖从 8/11 提升到 11/11。**《安全帽》的上游 OCR 缺号、错字和超长表格仍需要 REVIEW，不宣称全文 OCR 或所有法律条号均正确。

本次没有重新入库、重建索引、启动数据库、提交 MinerU 解析或调用 VLM；Agent、Retrieval、Safe-team 均未修改。复用 Phase 5.3 的 3 份 PDF/ZIP，回放前校验源 PDF 和 ZIP SHA256，原人工正文边界标注未修改。

## 前后对比

“之前”为 Phase 5.3 同一批缓存经过过滤后的输出，不是本轮新版解析器的未过滤输出。之前的证据保存在 `.output/legal-pdf-audit/`；本轮输出独立保存至 `.output/phase5-4-quality/`。

| 样本 | 正文误删 Block 前→后 | 非正文残留 Block 前→后 | 编号层级冲突前→后 | 正文表格覆盖前→后 | 正文文本覆盖告警前→后 |
|---|---:|---:|---:|---:|---:|
| 安全帽 GB 2811-2019 | 5→0 | 2→0 | 1→0 | 0/3→3/3 | 61→0 |
| 供用电规范 GB 50194-2014 | 0→0 | 0→0 | 43→0 | 8/8→8/8 | 1→0 |
| 建设工程安全生产管理条例 | 0→0 | 0→0 | 0→0 | 无表格 | 0→0 |

| 样本 | Clause 容器数前→后 | Chunk 前→后 | 新版技术容器数 | 新版超长 Chunk |
|---|---:|---:|---:|---:|
| 安全帽 | 12→23 | 12→30 | 14 | 2 |
| 供用电规范 | 204→203 | 206→205 | 0 | 0 |
| 条例 | 71→71 | 71→71 | 0 | 0 |

技术容器使用 `UNNUMBERED@<elementIndex>` 或 `TABLE@<elementIndex>`，不是猜出的法律条号，不纳入“成功识别真实条号”的宣称。《安全帽》另外 9 条记录通过了编号格式/当前父级检查，但仍可能含 OCR 错字、重复编号。非空 Clause_no=100% 仅表示字段有值，不能代替真实条号识别率。

## Task 1：Section Filter

- 支持 `目次`、`目 次`、`目录`、`目 录`、`TABLE OF CONTENTS`、`Contents`，修复空白归一化后英文标题无法匹配的问题。
- 目录页码结构兼容点线、省略号、连续空白、括号页码以及多行目录 Block。已确认正文内部的省略号不会被当作前部目录删除。
- 前言删除同时要求：处于文档前部、是可信 HeadingBlock 且标题为前言、后面有可确认的正文起点。
- 由于生产 Provenance 没有页码，文档前部采用 Block 序位保守判断：前 1/3，最多 80 个 Block，极短文档至少检查前 3 个序位；这不是物理页码判断。
- 正文起点既支持第一章、第一条、1 总则/范围等结构，也支持紧邻独立标题后的“本标准规定了…”等范围陈述。OCR 损坏范围标题时，保留该标题及前一连续标题，不尝试改写 OCR 文本。
- 缺少可靠正文边界、标题不可信或前言出现在正文之后时，不执行前言区域删除，并通过质量警告暴露不确定性。
- 附录正文中引用“附录A”的普通段落不会触发整段删除。

实际样本中，《安全帽》的“目次”及整段目录已删除；原 Block 29–33（正文标题、损坏的范围标题、三段范围说明）均保留，并进入 Chunk。

## Task 2：保留 Block 边界并增强结构解析

原链路将 MinerU Block 展平成字符串再按换行逐条解析，导致软换行被误当成新的条款边界。现在 PDF 适配器使用 `LegalSourceBlock` 保留类型及边界，清洗后一个 PDF Block 对应一个 Element；原始换行仍保留在 rawText，规范化文本中的软换行合并为空格。

PDF 专用解析逻辑只在独立 Block 开头判断编号，并校验条号与已识别章、节的父子关系以及非空正文。`5.0.1条…`、`第5.0.1条…` 等引用写法不能生成新编号 Clause；不合法编号以原文保留并告警。

利用 SOURCE_HEADING 识别较长或紧凑章标题；当 OCR 漏掉中间章节标题时，后续章节可以用其子级编号作为证据，避免沿用旧章号。没有编号证据的异常跳号保留待复核，不自动纠正数字。

实际 Chunk 抽查：

```text
6 配电设施 / 6.2 配电室 / 6.2.1
配电室的选址及对其他专业的要求应符合本规范第 5.0.1条、第5.0.2条的有关规定。

3 供用电设施的设计、施工、验收 / 3.1 供用电设施的设计 / 3.1.1
供用电设计应按照工程规模、场地特点、负荷性质、用电容量、地区供用电条件,合理确定设计方案。

10 办公、生活用电及现场照明 / 10.1 办公、生活用电 / 10.1.1
办公、生活用电器具应符合国家产品认证标准。
```

6.2.1 不再截断，也不再在第 6 章下生成虚假的 5.0.1。TXT 仍使用原来的逐行入口和解析分支，相关 TXT 单元回归通过。

## Task 3：正文表格保留

- HtmlTableBlock / TableBlock 作为 TABLE 类型进入清洗及结构解析，不对表格单元格中的数字创建法律条款。
- 有当前 Clause 时，将表格作为 TABLE 子单元保留；没有可靠条号时使用显式技术容器，产生 REVIEW 警告，不丢弃正文或表格。
- Chunker 仅增加 TABLE 原子保留判断，避免 HTML 实体中的分号及单元格标点被当成句末切分；不改变 token 阈值。
- 质量报告的 tableCount 按实际 TABLE Element 统计，技术容器或编号不确定性将触发 REVIEW。

《安全帽》原 Block 83、157、168 的三个正文表格现已完整进入 Chunk，原来缺失的“产品类别”“检验项目名称”等表头均可检索到文本。其中两个完整表格 Chunk 的 heuristic tokenCount 为 702、980，超过现有 600 阈值，仍标记为超长；本阶段没有引入跨行/跨页表格重排或 VLM。

表格覆盖的口径是缓存 MinerU 表格文本经规范化后在 Chunk 中完整出现，并不表示修正了原始 OCR 单元格错误或完成了所有表格版式验收。

## 测试和复现

新增 `LegalPdfHardeningTest`，19 项测试覆盖目录标题变体、目录标题带页码、前言可信度/位置/边界保护、重复封面标题、OCR 损坏范围标题、跨 Block 与 Block 内引用、编号无正文、非法层级、紧凑/长章标题、异常跳号的子级证据、表格技术容器和 HTML 原子保留。

最终回归结果：**61 项，56 通过，5 跳过，0 失败**。跳过项是需要额外 TXT 数据集或显式在线/入库开关的既有集成测试；本次未启用这些开关。三份缓存回放约 1 秒，无网络请求。

Phase 5.4 的验收开关独立于已有全文质量严格模式：`legal.pdf.audit.hardening=true` 强制断言三份样本正文误删、非正文残留、文本覆盖缺口、编号层级冲突、表格覆盖缺口为 0，并检查标题边界用例全部通过。旧 `legal.pdf.audit.strict=true` 保留 OCR 条号召回及超长块等更广泛的要求，没有降低或移除。

```powershell
Set-Location 'D:\1-project\ragent'
.\mvnw.cmd -o -pl rag `
  '-Dtest=LegalPdfHardeningTest,LegalPdfOfflineAuditTest' `
  '-Dlegal.pdf.cache.dir=D:\1-project\ragent\.output\legal-pdf-cache' `
  '-Dlegal.pdf.audit.out=D:\1-project\ragent\.output\phase5-4-quality' `
  '-Dlegal.pdf.audit.hardening=true' test
```

## 证据文件

- [本轮机器诊断报告](D:/1-project/ragent/.output/phase5-4-quality/REPORT.md)
- [本轮汇总指标](D:/1-project/ragent/.output/phase5-4-quality/summary.json)
- [安全帽正文及 Chunk 对照](D:/1-project/ragent/.output/phase5-4-quality/ee1668c18709dff5a9b4b4ae4a6f66060c13e7aa4994d07c29bb71c15c844226/evidence.md)
- [供用电规范正文及 Chunk 对照](D:/1-project/ragent/.output/phase5-4-quality/467cf4ca7017a40e7884b08fed362bcdd2f092170a19852ace0ab4f365d3cf99/evidence.md)
- [条例正文及 Chunk 对照](D:/1-project/ragent/.output/phase5-4-quality/eea49cb9d5fd815e21339a3a99398674fc2c7026e312464bc0d4db55409c68f0/evidence.md)
- [Phase 5.3 原基线](D:/1-project/ragent/LEGAL_PDF_OFFLINE_QUALITY_REPORT.md)

每个样本目录同时保留 before.json / after.json、cleaned.txt 和 block-decisions.json。机器报告的 before/after 是“本版解析器过滤前/后”，与本报告中的跨阶段对比口径不同。

## 限制与停止边界

本轮“层级错误=0”指上述独立编号父级检查及已抽查条款的结果；OCR 缺号部分保存在技术容器中，其法律归属仍待复核，不算成功恢复的正式条号。没有依据文件名猜号，也没有用 VLM 补写原文。

知识库没有更新；入库幂等 parserVersion 契约未调整，后续若要应用到已入库文档仍需单独处理安全替换流程。本阶段到离线加固及验证为止，不执行重新入库。
