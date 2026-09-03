# ragent 本地开发启动指南

仓库：`D:\\1-project\\ragent`

## 项目结构

- `bootstrap/`：Spring Boot backend，端口 9090，路径 `/api/ragent`。
- `frontend/`：React + Vite frontend，端口 5173。
- `resources/docker/`：RocketMQ 等 Compose 文件。
- `scripts/windows/`：已有 PostgreSQL、RustFS 启动及数据库初始化脚本。
- `scripts/`：统一开发启动、停止、检查脚本。

## 依赖服务

PostgreSQL + pgvector：`127.0.0.1:15432`；Redis：`127.0.0.1:6379`，密码 `123456`；RustFS API：`http://localhost:9000`；RocketMQ NameServer：`127.0.0.1:9876`，proxy 宿主机端口 `18080-18082`（按需）；Ollama：`http://localhost:11434`（可选）；Elasticsearch：`127.0.0.1:9200`，当前 `rag.keyword.type=none`；MinerU：外部 API，可选。

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
