# Windows + IDEA 使用公共云中间件启动 Ragent

本文档用于 Windows 本地开发：PostgreSQL 和 RustFS 在本地 Docker 运行，Redis 和 RocketMQ 使用公共云。

## 1. 本地必须启动的组件

在项目根目录执行：

```powershell
.\scripts\windows\start-ragent-required-middleware.ps1
```

该脚本会创建或启动：

- `ragent-postgres`：`registry-1.docker.io/pgvector/pgvector:pg16`，宿主机端口 `15432`
- `ragent-rustfs`：`registry-1.docker.io/rustfs/rustfs:1.0.0-alpha.72`，API 端口 `9000`，控制台端口 `9001`

RustFS 默认账号和密钥都是 `rustfsadmin`。

## 2. 初始化 PostgreSQL

首次启动 PostgreSQL 后执行：

```powershell
.\scripts\windows\init-postgres.ps1
```

脚本会按顺序执行：

1. `resources/database/schema_pg.sql`
2. `resources/database/init_data_pg.sql`

项目默认 JDBC 地址已配置为 `jdbc:postgresql://127.0.0.1:15432/ragent?client_encoding=UTF8`，避免和 Windows 上已有的 PostgreSQL `5432` 冲突。如果你确认本机 `5432` 没有被占用，也可以运行脚本时传入 `-PostgresHostPort 5432`，并通过 `RAGENT_POSTGRES_URL` 覆盖项目连接地址。

## 3. IDEA 运行配置

项目已提供两个 IDEA Run Configuration：

- `RagentApplication-public-cloud`
- `MCPServerApplication-public-cloud`

打开 IDEA 后可直接选择运行。默认唯一名是 `dingma`，建议在 Run Configuration 里把 `-Dunique-name=-dingma` 和 `-Dframework.cache.redis.prefix=dingma:` 改成你自己的英文标识。

在 IDEA 的 `RagentApplication` 和 `McpServerApplication` Run Configuration 中设置：

```text
Active profiles: public-cloud
```

`public-cloud` profile 已按教程内置公共云 Redis、RocketMQ、Nacos、`unique-name` 和 Redis key 前缀。你也可以用环境变量覆盖：

```text
RAGENT_UNIQUE_NAME=-<你的英文名或唯一标识>
RAGENT_REDIS_PREFIX=<你的英文名或唯一标识>:
RAGENT_REDIS_HOST=common-redis-dev.magestack.cn
RAGENT_REDIS_PORT=19389
RAGENT_REDIS_PASSWORD=<教程中的公共云 Redis password>
RAGENT_ROCKETMQ_NAME_SERVER=common-rocketmq-dev.magestack.cn:9876
RAGENT_NACOS_SERVER_ADDR=common-nacos-dev.magestack.cn:8848
BAILIAN_API_KEY=<阿里云百炼 API Key>
SILICONFLOW_API_KEY=<硅基流动 API Key>
```

如果更想完全按教程填写 VM options，也可以不启用 profile，直接在 VM options 中填写：

```text
-Dunique-name=-dingma
-Dframework.cache.redis.prefix=dingma:
-Dspring.data.redis.host=common-redis-dev.magestack.cn
-Dspring.data.redis.password=<教程中的公共云 Redis password>
-Dspring.data.redis.port=19389
-Drocketmq.name-server=common-rocketmq-dev.magestack.cn:9876
-Dspring.cloud.nacos.discovery.server-addr=common-nacos-dev.magestack.cn:8848
```

建议把 `dingma` 改成你自己的英文标识，避免和其他同学共用 Redis/RocketMQ 时互相影响。如果课程提供了不同的 RocketMQ NameServer，请用课程给出的 `host:port` 覆盖 `RAGENT_ROCKETMQ_NAME_SERVER`。Dashboard 地址 `http://common-rocketmq-dev.magestack.cn:8088` 只用于查看控制台，不能作为 `name-server`。

同时确认 IDEA 使用 JDK 17+。如果命令行要运行 Maven Wrapper，还需要在 Windows 系统环境变量里设置 `JAVA_HOME`，并把 `%JAVA_HOME%\bin` 加入 `Path`。

如果你已经把阿里云百炼和硅基流动 API Key 写进了 `application.yaml`，可以不再额外配置 `BAILIAN_API_KEY` 和 `SILICONFLOW_API_KEY` 环境变量；但不要把包含真实密钥的文件提交到公共仓库。

## 4. 验证

执行：

```powershell
.\scripts\windows\verify-ragent-public-cloud-env.ps1
```

确认 PostgreSQL、RustFS、Redis、RocketMQ、AI Key 都可用后，在 IDEA 启动：

1. `RagentApplication`
2. 可选：`McpServerApplication`
