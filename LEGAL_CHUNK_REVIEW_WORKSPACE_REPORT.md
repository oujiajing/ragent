# Legal Chunk 列表复核与编辑增强实施报告

日期：2026-09-05

## 当前结果

已将复核能力回收到原有 LEGAL Chunk 列表：Legal 文档完成分块持久化后自动检测；列表状态显示检测结果；用户可以按章、按复核状态筛选，并输入页码跳转。独立复核面板和“运行本轮检测”入口已移除。

## 已实现

- `LegalDocumentProcessingService` 在 Clause/Chunk 持久化后调用复核检测。检测异常记录为独立的 `DETECTION_FAILED`，不会覆盖分块任务成功状态。
- 新增 `t_legal_review_signal` 与 `t_legal_review_run`，保存问题证据、关联 Clause/Chunk、检测版本、输入指纹、运行状态和人工结论。
- 条款缺号检测按文档、内容角色、章/节作用域和原文顺序执行；条内枚举检测优先使用完整 Clause children。
- Chunk 查询新增 `chapterNo` 与 `reviewStatus` 服务端筛选，保留启用状态筛选，并在筛选后分页。
- Chunk VO 增加章节、条款、页码和复核状态字段。
- LEGAL 列表增加章节筛选、复核状态筛选和页码输入跳转；切换筛选条件回到第 1 页。
- 点击状态可查看检测原因、关联条款/分块和证据，并逐 Signal 提交“确认正常/确认异常”。
- Chunk 编辑、创建、删除后会使关联旧 Signal 失效并进入待重检状态；输入指纹变化后重新审计会产生新的 Signal 记录，保留历史结论。
- 复核结论使用服务端操作者、时间和 expected version；并发冲突不自动覆盖。

## 验证

- 后端编译：PASS。
- 定向后端测试：8 passed, 0 failed, 0 errors, 0 skipped。
- 前端 ESLint：PASS。
- 本地 PostgreSQL migration：PASS；`t_legal_review_signal` 和 `t_legal_review_run` 已创建，20 个 Signal 字段可见。
- JSONB Chunk 关联与内容指纹查询：PASS。
- Vite build：仍受现有依赖树中缺少 `highlight.js/lib/languages/sql_more` 阻塞，未修改无关依赖。
- 真实前端/API/MQ Smoke：尚未完成。

## 边界与限制

- 当前检测器覆盖条款缺号和条内枚举缺号；已有 OCR、层级、超长和表格 Warning 的统一映射、文档级问题入口及完整章节树仍需后续补齐。
- 当前复核详情提供证据和结论入口；PDF 原文授权打开、精确页码语义和相邻 Chunk 对照仍需接入现有文件预览能力。
- 编辑后的旧 Signal 会失效并显示待重检，完整的后台异步重检队列和 30 份存量 PDF 批量补算仍需补充。
- 没有修改 Quality Gate、`indexEligible`、向量、BM25、RetrievalEngine 或 Safe-team。

## Git

本次实现 commit：`8e9e714f`（`feat: integrate legal review into chunk list`）。ragent 工作区原有多个阶段修改和运行产物，均未纳入该 commit。
