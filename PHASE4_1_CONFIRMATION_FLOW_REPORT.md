# Phase 4.1 Confirmation Flow Report

## 1. 状态机

`CREATED -> CONFIRMATION_REQUIRED -> CONFIRMED -> TASK_CREATED`；Evidence 缺失或 Safe-team 失败进入 `FAILED`。已创建任务再次确认返回 `ALREADY_CREATED`，不会重放写请求。

## 2. Assessment 数据模型

`HazardAssessment` 保存隐患描述、分类、风险等级/说明、整改建议、验收标准、Evidence、Tool Proposal、状态、taskId/taskStatus、错误原因、时间和 Demo Trace。生产实现写入 `safeguard_hazard_assessment`，详情读取时以状态列为准。

## 3. API

- `POST /api/ragent/agent/hazard-assessment`：生成并持久化评估，返回 `assessmentId` 与 `CONFIRMATION_REQUIRED`。
- `GET /api/ragent/agent/hazard-assessment/{assessmentId}`：返回完整评估。
- `POST /api/ragent/agent/hazard-assessment/{assessmentId}/confirm`：经状态/Evidence 校验后创建整改任务。

## 4. Tool / HITL 流程

Agent 只生成 proposal；用户确认后，Assessment Service 调用 `RectificationTaskCreator`，由 agent 模块转入既有 `SafeTeamToolExecutor.executeForDevelopment`，再经 Safe-team REST API。LLM 无法直接执行写 Tool。

## 5. 测试结果

`HazardAssessmentServiceTest` 12 项、`HazardAssessmentConfirmationTest` 2 项、`HazardAssessmentE2ETest` 1 项、`SafeTeamToolExecutorTest` 3 项通过；编译通过。

## 6. Demo

输入“地下室临边没有设置防护栏杆”会识别为临边防护/高风险，复用法规 Evidence 生成整改建议和验收标准，返回 `CONFIRMATION_REQUIRED`。配置 Safe-team token 后调用 confirm，任务创建结果写入 `taskId` 并返回 `TASK_CREATED`。

## 7. Limitations

当前确认接口未引入复杂工作流；Safe-team 的责任人、组织等业务字段仍由业务系统校验，409 失败不自动重试。真实 Demo 需要可用 Safe-team 环境和有效权限。
