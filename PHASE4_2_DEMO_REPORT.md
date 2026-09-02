# Phase 4.2 Live Demo Report

日期：2026-09-03
结论：**PASS**

## 1. 环境配置

- Safe-team：`http://localhost:18081`，profile=`local`
- Safe-team PostgreSQL：`localhost:15433/pingan_banzu_verify`
- ragent：`http://localhost:19090/api/ragent`，profile=`local`
- Ollama：`http://localhost:11434`
- Chat 模型：`qwen3.5:4b`，Q4_K_M，GPU 推理
- Embedding：既有 TEI/BGE-M3，端口 `18080`
- `SAFE_TEAM_DEV_TOKEN`：通过 Safe-team `/api/auth/login` 动态获取，仅注入 ragent 子进程，未打印、未写文件

18080 实际由 `tei-bge-m3` 容器使用，因此 Safe-team 按已确认回退方案运行在 18081。

## 2. 服务启动与 JWT 认证

Safe-team Actuator health 返回 `UP`，admin 登录成功并取得真实 JWT。ragent 启动时注入 `SAFE_TEAM_BASE_URL=http://127.0.0.1:18081`、JWT、`SAFE_TEAM_LIVE=true` 与 local profile。四个 Safe-team Tool 注册成功。

Ollama `/v1/chat/completions` 使用 `reasoning_effort=none`，解决 Qwen3.5 reasoning 占满输出、`message.content` 为空的问题。

## 3. 完整请求链路

输入：`地下室临边没有设置防护栏杆`

1. `POST /api/ragent/agent/hazard-assessment`：成功
2. `GET /api/ragent/agent/hazard-assessment/{assessmentId}`：成功，持久化数据完整
3. `POST /api/ragent/agent/hazard-assessment/{assessmentId}/confirm`：显式传入 companyId=4、departmentId=101109、teamId=1011001，成功创建 Safe-team 任务
4. 再次调用 confirm：返回 `ALREADY_CREATED`，未重复创建

## 4. Assessment 状态变化

- assessmentId：`231af5ca-8e62-4411-a914-556ca4be505a`
- riskLevel：`高`
- Evidence：5 条
- 初始状态：`CONFIRMATION_REQUIRED`
- 确认后状态：`TASK_CREATED`

```text
CONFIRMATION_REQUIRED -> CONFIRMED -> TASK_CREATED
```

## 5. Safe-team 任务结果

- taskId：`48`
- taskStatus：`PENDING_ASSIGN`
- Tool：`create_rectification_order`
- 创建次数：1

## 6. 幂等验证

同一 assessmentId 第二次 confirm 返回：

```text
status=ALREADY_CREATED
taskId=48
```

Safe-team 未创建重复任务。

## 7. Agent Trace

最终 Trace 包含：

1. `UNDERSTAND_HAZARD`
2. `RETRIEVE_EVIDENCE`
3. `GENERATE_SUGGESTION`
4. `WAIT_CONFIRM`
5. `TOOL_CALL`：调用 `create_rectification_order`
6. `TASK_RESULT`：整改任务已创建，taskId=48

## 8. 测试与修复

- Ollama 请求体测试：9 项通过
- Assessment/Safe-team 相关测试：18 项通过
- PostgreSQL `Instant` 参数已显式转换为 JDBC `Timestamp`
- Confirm API 新增显式组织归属参数，业务 ID 不由 LLM 生成或猜测

## 9. 已知限制

1. Safe-team 端口使用 18081；18080 保留给 TEI Embedding。
2. 本地 Qwen3.5 仅用于 Chat，Legal RAG 的 Embedding/检索架构未改变。
3. Trace 当前最终步骤名为 `TOOL_CALL`、`TASK_RESULT`，尚未统一为 `CREATE_RECTIFICATION_TASK`。
4. Confirm 的组织归属必须由调用方显式提供，Safe-team 继续负责权限、数据范围和业务状态校验。
