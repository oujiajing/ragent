# SafeGuard Agent Final Report

## 1. 产品背景与用户痛点

施工现场隐患描述分散、法规依据查找成本高、整改任务创建容易脱离人工确认。SafeGuard 将隐患识别、法规证据、整改建议和 Safe-team 任务创建串成可审计闭环。

## 2. 产品方案与 Agent Workflow

用户输入隐患 → 分类与风险判断 → Legal RAG 检索 → 输出 Evidence、整改建议和验收标准 → `CONFIRMATION_REQUIRED` → 用户确认 → Safe-team REST Tool → 返回 `taskId`。写操作只在 Confirm API 中执行。

## 3. RAG 架构

沿用现有 ingestion、Parser、Embedding、PgVector、Elasticsearch/BM25、RRF、Rerank、Citation 和 `search_knowledge`，SafeGuard 仅增加施工安全领域组合层，不复制或修改核心检索链路。

## 4. Tool 与 HITL 设计

LLM 只能生成 `create_rectification_order` proposal。确认接口校验 Assessment 状态和 Evidence 后，通过窄接口进入既有 `SafeTeamToolExecutor`，由 Safe-team 作为权限、数据范围、状态机和 version 的事实源。重复确认返回 `ALREADY_CREATED`，409 不自动重试。

## 5. 数据规模与 Evaluation

本阶段未扩展语料或检索规模；评估复用 Phase 3 法规语料与 Evidence 结果。新增本地 E2E、确认幂等、Evidence 校验和 Safe-team 成功路径测试；相关测试全部通过。全量测试仍受既有 Milvus/远程模型配置影响，详见 Phase 4.1 报告。

## 6. Demo Cases

### Case 1：临边防护缺失

- 输入：地下室临边没有设置防护栏杆
- AI 分析：临边防护，高风险，存在人员坠落风险
- 法规依据：返回检索到的施工安全条款 Evidence
- 整改建议：设置连续、牢固的防护栏杆，并按 Evidence 进行现场验收
- 任务结果：确认后调用 Safe-team，返回 `taskId`；再次确认不重复创建

### Case 2：脚手架剪刀撑缺失

- 输入：脚手架没有设置剪刀撑
- AI 分析：脚手架，高风险，结构稳定性不足
- 法规依据：返回脚手架相关条款 Evidence
- 整改建议：按条款补设剪刀撑并检查连接和整体稳定性
- 任务结果：进入确认状态；确认后由 Safe-team 返回任务结果

### Case 3：临时用电问题

- 输入：施工现场配电箱没有防护
- AI 分析：临时用电，中风险，存在触电和误操作风险
- 法规依据：返回临时用电相关条款 Evidence
- 整改建议：完善配电箱防护、接地和警示，并现场验收
- 任务结果：进入确认状态；确认后由 Safe-team 返回任务结果

## 7. Limitations

真实 Safe-team Demo 需要运行环境提供 `SAFE_TEAM_LIVE=true`、`SAFE_TEAM_DEV_TOKEN` 和有效权限。本次执行环境未配置 token，因此未进行真实业务写入；本地 MockWebServer E2E 已验证完整调用链。组织、责任人、业务字段仍由 Safe-team 校验。
