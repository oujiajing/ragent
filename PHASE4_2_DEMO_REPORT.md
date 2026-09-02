# Phase 4.2 Live Demo Report

日期：2026-09-02  
结论：**BLOCKED（Bean 歧义已修复；Chat 模型不可用；未产生业务写入）**

## 1. 环境配置

- Safe-team 仓库：`D:\1-project\Pingan_Banzu`
- Safe-team profile：`local`
- 既定端口 `18080`：被本机 Clash Verge 网络服务占用/拦截，当前会话停止服务时返回 `Access is denied`
- 回退端口：`http://localhost:18081`
- Safe-team PostgreSQL：`localhost:15433/pingan_banzu_verify`
- ragent profile：`local`
- ragent 目标地址：`http://localhost:19090/api/ragent`
- `SAFE_TEAM_BASE_URL`：运行时注入 `http://127.0.0.1:18081`
- `SAFE_TEAM_DEV_TOKEN`：通过真实登录动态获取，仅注入子进程环境，未打印、未写文件
- `SAFE_TEAM_LIVE=true`

## 2. 服务启动与 JWT 认证

Safe-team 使用 `--server.port=18081` 启动成功，Actuator health 返回 `UP`。登录接口 `POST /api/auth/login` 使用 admin 本地测试账号成功返回 `accessToken`。

- token 获取时间：2026-09-02 20:00:29 +08:00
- 当前登录身份：admin
- token 未记录明文

## 3. ragent 启动结果

停止了占用旧 bootstrap JAR 的 public-cloud ragent PID 37660，随后重新打包成功。以 local profile、19090 端口及真实 Safe-team JWT 启动时，Spring Context 初始化失败：

```text
SafeTeamRectificationTaskCreator constructor parameter 0
required a single SafeTeamToolExecutor bean, but 4 were found:
searchRectificationOrders
getRectificationOrder
createRectificationOrder
issueRectification
```

经用户授权，已在 `SafeTeamRectificationTaskCreator` 构造参数上增加 `@Qualifier("createRectificationOrder")`，只消除上述 Bean 歧义。专项测试 4 项通过，ragent 随后以 local profile 在 19090 启动成功，四个 Safe-team Tool 注册成功。

真实评估请求随后进入 Legal RAG，但 Chat 模型全部不可用：

- 百炼：HTTP 400 `Arrearage`（账号欠费）
- Ollama：本机 11434 未运行，且没有配置所需的 `qwen3:8b-fp16`
- OpenAI-compatible 环境地址：TLS 主机名与证书不匹配
- 证书覆盖的候选域名：HTTP 502
- 标准 OpenAI 端点：当前两组环境 key 均返回 HTTP 401

最终接口返回 `C000001: No Chat model candidates available`，未生成 Assessment。

## 4. 完整请求链路

请求执行结果：

1. ragent `/auth/login`：成功，admin userId=`2001523723396308993`
2. `POST /api/ragent/agent/hazard-assessment`：失败，`No Chat model candidates available`
3. 因无 `assessmentId`，未执行 GET、confirm 和重复 confirm

## 5. Assessment 状态与 Safe-team 任务结果

- Assessment：未创建
- Assessment 状态变化：未发生
- Safe-team taskId：无
- Safe-team taskStatus：无
- Safe-team 业务写入：0 次

## 6. 幂等验证与 Agent Trace

由于 ragent 启动失败，未执行真实 confirm 幂等验证，也未获得真实 Trace。现有实现中的最终步骤名称为 `TOOL_CALL` 和 `TASK_RESULT`，与本阶段期望的 `CREATE_RECTIFICATION_TASK` 命名不一致；本轮遵守“不要修改代码”，未调整。

## 7. 已知限制与恢复条件

1. 18080 需由管理员权限停止/调整 Clash Verge 服务，或继续使用已验证可用的 18081。
2. `SafeTeamToolExecutor` Bean 注入歧义已完成最小修复。
3. 必须恢复至少一个可用 Chat 模型后，才能生成 Assessment 并继续真实 confirm。
4. Safe-team 18081、ragent 19090、PostgreSQL 和 MinIO 当前保持运行。
