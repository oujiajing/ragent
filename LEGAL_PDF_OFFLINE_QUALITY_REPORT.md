# PDF 解析、清洗、分块低成本验证

日期：2026-09-03。结论：离线验证流程已建立；3 份真实样本中，2 份存在内容质量问题，1 份通过已标注检查。不能据此认为 30 份 PDF 均已验收。

## 成本与范围

- 复用本地《供用电规范》完整 MinerU ZIP，并从此前已完成任务下载《安全帽》和《建设工程安全生产管理条例》结果。
- 新增 PDF 上传 / MinerU 解析提交：0。缓存准备有查询和下载网络请求，后续回放网络请求为 0。
- 不启动 Spring，不访问 PostgreSQL、Redis、RustFS、Embedding 或 VLM；Maven 使用 `-o` 离线模式。
- 回放复用生产 `MinerUResultUnpacker → LegalSectionFilter → LegalDocumentImportAdapter → LegalCleaningPipeline / LegalStructureParser / LegalChunker`。图片存储用本地 mock 返回基于图片 hash 的标识，关闭 VLM；因此不评估图片描述能力。
- 3 份完整回放约 1.2–1.6 秒，已有依赖及编译产物下 Maven 全程约 4.7 秒。
- 本次只增加测试工具、人工标注和报告；不修复生产过滤/解析实现，不更新知识库。

## 真实样本结果

| 样本 | Clause 前→后 | Chunk 前→后 | 正文误删 Block | 非正文残留 Block | 正文文本覆盖告警 | 层级不匹配候选 | 结论 |
|---|---:|---:|---:|---:|---:|---:|---|
| 供用电规范 GB 50194-2014 | 227→204 | 231→206 | 0 | 0 | 1 | 43 | 需修复/复核 |
| 安全帽 GB 2811-2019 | 14→12 | 14→12 | 5 | 2 | 61 | 1 | 不通过 |
| 建设工程安全生产管理条例 | 71→71 | 71→71 | 0 | 0 | 0 | 0 | 已标注检查通过 |

三份样本的 Clause_no / Hierarchy 非空字段比例均为 100%，空 Chunk / 超过 600 heuristic tokens 的 Chunk 均为 0。这些指标不代表条号识别率、层级正确率或正文完整性。

当前保留的非正文 Block 内容没有匹配到 Chunk，因此本次“疑似噪声 Chunk”检查为 0；《安全帽》目录仍残留于过滤输出，正文还被误删，不能将零噪声 Chunk 宣称为整个 Pipeline 质量通过。

正文覆盖告警使用去空白、去 HTML 标签后的完整文本匹配；它能定位丢失/拆断，但格式或分块边界变化也可能产生告警，61 个告警不能直接解读为 61 个已人工确认的丢失条款。层级候选检查只适用于本批数字章号标准，不能当作通用法律编号规则。

## 已核对的内容证据

### 1. 安全帽：上游 OCR 缺号、错字，过滤进一步删除范围正文

核对当前源 PDF 物理第 4 页和 ZIP 内 origin.pdf 第 4 页，两者均清晰显示 `1 范围`、`3.1 安全帽`、`3.2 帽壳`。原始 MinerU Markdown 却出现 `范团`、`.1`、`安全幅` 等识别结果。原页并未缺失这些字。

在过滤输出中，Block 29–33 被当作前言删除：正文标题、范围标题和三段范围说明。过滤器直到后续 `2规范性引用文件` 才恢复正文。

Block 7–8 为 `目 次` 和整段目录，仍被保留；当前规则只识别“目录”，没有覆盖“目次”。

六个抽样条号中缺失 `3.1`、`3.2`、`5.2.1`、`5.2.16`、`5.2.17`；`5.2.4` 存在但对应正文也有明显 OCR 错字。对原页直接核对了 3.1/3.2；其余缺号作为后续复核项保留。

物理第 5 页表 1 在原 PDF 和原始 HTML 中存在。3 个正文 HTML 表格 Block（83、157、168，对应缓存物理页 5、8、9）均通过过滤，但未完整进入 Clause/Chunk；表头“产品类别”“检验项目名称”“不合格质量水平”在结果 Clause 与 Chunk 中均找不到。当前首个 Chunk 已从 5.2.2 开始。

源文件：[安全帽 PDF](<C:/Users/ojj/Desktop/法律法规数据集/《安全帽》GB 2811-2019.pdf>)。

### 2. 供用电规范：跨行引用被误识别为新条款

源 PDF 物理第 16 页（印刷页 10）中，6.2.1 是一条完整规定，后半句引用 5.0.1、5.0.2。MinerU 将引用放在下一行，现有清洗/结构解析产生：

```text
Chunk 51：6 配电设施 / 6.2 配电室 / 6.2.1
配电室的选址及对其他专业的要求应符合本规范第

Chunk 52：6 配电设施 / 6.2 配电室 / 5.0.1
条、第5.0.2条的有关规定。
```

错误在进入 Chunker 前已存在于 Clause：应合并的续行被当成新条号。另有第 3、10、11 章条款挂到旧章号下的候选，共 43 项，详细路径保存在 summary.json，未逐条作视觉核验。

原 PDF 物理第 3 页确认目录标题为“目次”；本样本目录恰好被前言过滤区域覆盖，因此没有残留，并不能证明“目次”本身的识别能力已正确。物理第 7 页抽查 1.0.1–1.0.4 的条文与缓存；正文 8 个 HTML 表格通过文本完整覆盖检查，表格布局尚未逐表人工验收。

源文件：[供用电规范 PDF](<C:/Users/ojj/Desktop/法律法规数据集/《建设工程施工现场供用电安全规范》GB 50194-2014.pdf>)。

### 3. 条例：对照样本通过

原 PDF 物理第 1 页核对“第一章 总则”和前几条正文，71 条解析结果与过滤前一致。抽样条号第一条、第二十六条、第七十一条存在，正文 Block 文本覆盖检查没有缺口。保留“附则”，不将其误认为“附录”。这是抽样检查通过，不是全文逐字 OCR 准确率证明。

源文件：[建设工程安全生产管理条例 PDF](<C:/Users/ojj/Desktop/法律法规数据集/中华人民共和国国务院令(第393号)　　建设工程安全生产管理条例__2004年第3号国务院公报_中国政府网.pdf>)。

## 合成边界用例

与真实 PDF 样本分开统计，调用同一个生产过滤器：

| 输入标题 | 应移除其后哨兵文本 | 当前结果 |
|---|---|---|
| TABLE OF CONTENTS | 是 | 未移除 |
| Appendix A | 是 | 未移除 |
| 本标准用词说明 | 是 | 已移除 |

前两项直接复现标题去除空白后仍与含空白字符串比较的问题。本次不改生产规则。

## 复现命令

本机已缓存好三份，日常诊断不需要 API key：

```powershell
& 'D:\1-project\ragent\scripts\test-legal-pdf-offline.ps1'
```

诊断模式导出全部问题，命令成功只代表工具运行完成。严格模式把已发现的质量问题作为失败返回（目前预期退出码 1）：

```powershell
& 'D:\1-project\ragent\scripts\test-legal-pdf-offline.ps1' -Strict
```

缓存准备脚本 `scripts/prepare-legal-pdf-cache.ps1` 接受 `-Pdf` 和 `-ResultZip`，或 `-Pdf` 和 `-BatchId`。后者仅下载已完成任务，不能新建解析任务。已有缓存 hash 校验通过时直接返回 CACHE_HIT；已验证不提供 key 时也可命中缓存。当前环境下载曾受代理 TLS 握手影响，用显式 `-DirectDownload` 恢复成功，不修改机器代理设置。

如果扩展到更多已有缓存，可提供 `-Dlegal.pdf.audit.expectations=<人工标注JSON绝对路径>`；格式同测试资源 `pdf-audit-expectations.json`。缺缓存、hash 不符或标注锚点找不到时立即失败，绝不自动转为在线解析。

## 产物与可信边界

- [机器诊断报告](D:/1-project/ragent/.output/legal-pdf-audit/REPORT.md)
- [汇总 JSON](D:/1-project/ragent/.output/legal-pdf-audit/summary.json)
- [供用电内容对照](D:/1-project/ragent/.output/legal-pdf-audit/467cf4ca7017a40e7884b08fed362bcdd2f092170a19852ace0ab4f365d3cf99/evidence.md)
- [安全帽内容对照](D:/1-project/ragent/.output/legal-pdf-audit/ee1668c18709dff5a9b4b4ae4a6f66060c13e7aa4994d07c29bb71c15c844226/evidence.md)
- [条例内容对照](D:/1-project/ragent/.output/legal-pdf-audit/eea49cb9d5fd815e21339a3a99398674fc2c7026e312464bc0d4db55409c68f0/evidence.md)

每个样本还导出 before.json / after.json（完整 Element、Clause、Chunk、质检）、cleaned.txt、block-decisions.json（每个 Block 的正文标注、保留状态、页码候选、过滤原因）。原始 Markdown、内容 JSON、PDF 和 ZIP 在 `.output/legal-pdf-cache/<PDF hash>/`，未提交到 Git。

manifest 记录当前源 PDF SHA256、ZIP SHA256、origin.pdf SHA256、历史 batchId 和缓存时间。历史上传 hash 不可查询；ZIP 的 origin.pdf 与当前 PDF 字节 hash 不同，因此不能宣称已做历史上传字节一致性认证。已通过文件名与抽样原页核对内容关联，页码候选仅来自原始内容 JSON，不写回生产数据。

人工正文边界独立于被测过滤器；完整率不是召回率；本次没有准确率全量真值。实际渲染检查遵循 PDF 技能，源 PDF 内容未修改。

## 验证记录

- 相关回归：18 项，17 通过、1 跳过（既有 `LegalStructurePreservationTest` 需要额外 TXT 数据配置），0 失败。
- 离线严格模式：2 项，1 通过、1 按预期失败；失败同时报告 2 个需复核真实样本和未通过的合成边界用例。
- 默认诊断输出与严格模式结果一致；重复使用缓存无新解析成本。
- 未执行 30 份在线重跑、重入库、重索引或业务库写入。

下一步若修复质量，优先处理《安全帽》上游 OCR 与正文起始恢复，其次处理跨行引用和章标题识别，再补齐英文标题/目次边界规则。当前授权任务是验证，修复项仅记录，不在本次实现。
