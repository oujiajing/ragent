# ragent 本地开发启动指南

仓库：`D:\\1-project\\ragent`

## 项目结构

- `bootstrap/`：Spring Boot backend，端口 9090，路径 `/api/ragent`。
- `frontend/`：React + Vite frontend，端口 5173。
- `resources/docker/`：RocketMQ 等 Compose 文件。
- `scripts/windows/`：已有 PostgreSQL、RustFS 启动及数据库初始化脚本。
- `scripts/`：统一开发启动、停止、检查脚本。

## 依赖服务

PostgreSQL + pgvector：`127.0.0.1:15432`；Redis：`127.0.0.1:6379`，密码 `123456`；RustFS API：`http://localhost:9000`；RocketMQ NameServer：`127.0.0.1:9876`，proxy 宿主机端口 `18080-18082`；TEI/bge-m3：`http://127.0.0.1:18083`（启动脚本执行 health + embedding smoke）；Ollama：`http://localhost:11434`（可选）；Elasticsearch：`127.0.0.1:9200`，当前 `rag.keyword.type=none`；MinerU：外部 API，可选。

## 第一次启动

```powershell
.\scripts\start-dev.ps1 -StartRocketMq
.\scripts\windows\init-postgres.ps1
```

需要 Docker Desktop、JDK 17+ 和 Node.js。首次初始化只执行现有数据库初始化脚本，不由统一启动脚本自动改库。

Ollama 不可用不会阻止启动，会输出 `[WARN] Ollama unavailable`。MinerU 需要时，在当前 PowerShell 会话设置 `MINERU_API_KEY`。

## 日常启动

```powershell
.\scripts\start-dev.ps1 -StartRocketMq
.\scripts\check-dev.ps1
```

访问：frontend <http://127.0.0.1:5173>；backend <http://127.0.0.1:9090/api/ragent>；RustFS Console <http://127.0.0.1:9001>。

## 停止方式

```powershell
.\scripts\stop-dev.ps1
```

只停止本项目 backend/frontend，不删除 Docker volume、数据库或 RustFS 数据；不要使用 `down -v`。

## 常见问题

- Docker 未启动：启动 Docker Desktop 后重试。
- 9090 或 5173 被占用：先执行 `check-dev.ps1`，脚本不会停止非本项目进程。
- Ollama unavailable：可选依赖，可使用其他已配置 provider。
- RocketMQ 未启动：需要消息消费或异步 RAG 流程时加 `-StartRocketMq`。
- Redis 密码不一致：统一使用 `resources/docker/redis-local.compose.yaml` 启动的 Redis，密码为 `123456`；脚本会停止旧的无密码 `ragent-redis-phase1`，但不会删除其 volume。
- Elasticsearch 默认未启用，不修改配置默认值。

## 总控启动与终端卡住排查

在 Windows PowerShell 5.1（包括 Conda base 环境）中执行：

    cd D:\1-project\safeguard-agent
    .\scripts\start-all-dev.ps1 -StartRocketMq

总控脚本直接调用两套项目脚本，开关通过具名参数传递。Java/Vite 的标准输入与启动终端隔离，服务启动后可以继续输入命令，关闭启动窗口也不会停止服务。

进入 ragent 后先打印 `[STEP]`。Docker 检查最多等待 30 秒，Compose 最多 180 秒，构建最多 600 秒，应用 HTTP 就绪最多 120 秒；长步骤每 5 秒打印 `[WAIT]`。失败会显示 `[FAILED]` 并返回错误，不会继续打印“全部启动成功”。

后台使用 local profile。构建显式跳过 Spotless 自动改写，仅生成构建产物。前端使用固定 5173 和 strictPort，端口冲突不自动换号。

完成条件：backend 的 `/api/ragent/` 返回 HTTP 200 和现有认证 JSON，frontend 返回 HTTP 200 和 HTML。backend 没有公开 actuator health，此处验证的是 API 可用性，不代表完整业务/RAG 验收。

日志位于 `.codex-run/`：

- `docker-last.log`：最近一次 Docker 命令；
- `ragent-build.log`：Maven 构建；
- `ragent-backend.out.log` / `ragent-backend.err.log`：backend；
- `ragent-frontend.out.log` / `ragent-frontend.err.log`：frontend。

诊断时检查日志修改时间，避免把上一次启动的成功记录当作本次结果。`check-dev.ps1` 会独立验证 HTTP，失败时返回非零退出码。

启动工具回归测试：

    powershell.exe -NoProfile -File scripts\test-dev-runtime.ps1

测试覆盖输入 EOF、stdout/stderr 并发读取、参数转义、超时、非零退出码、TCP/HTTP 区别和应用提前退出。
