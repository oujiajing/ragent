# Ragent Windows + IDEA + 公共云中间件完整启动教程

本文适用于 Windows 本地开发环境。当前方案是：本地 Docker 运行 PostgreSQL + RustFS，公共云使用 Redis + RocketMQ + Nacos，后端在 IDEA 或 Docker 中启动，前端使用 Vite 启动。

## 1. 启动前准备

已安装：

- Docker Desktop
- IDEA
- JDK 17+
- Maven
- Node.js，只有开发前端时需要

如果只在 IDEA 中启动后端，IDEA 配好了 JDK 即可；如果要在 PowerShell 直接执行 `java`、`mvn`，需要配置 `JAVA_HOME`。

PowerShell 临时配置示例，路径按你的 JDK 安装位置修改：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
java -version
mvn -version
```

## 2. 公共云是什么意思

公共云中间件就是课程或团队提供的一套大家共用的 Redis、RocketMQ、Nacos。

因为是共用，所以必须配置自己的隔离标识，否则会互相影响。最重要的是：

- `unique-name`：隔离 RocketMQ topic、consumer group、producer group
- `framework.cache.redis.prefix`：隔离 Redis key

当前本地已经验证可用的隔离配置是：

```yaml
unique-name: -dingma23a45

framework:
  cache:
    redis:
      prefix: "dingma23a45:"
```

如果教程或老师给了你专属标识，优先使用专属标识，并保持两处一致。

## 3. 本地启动 PostgreSQL 和 RustFS

在项目根目录执行：

```powershell
cd D:\1-project\ragent
.\scripts\windows\start-ragent-required-middleware.ps1
```

该脚本会启动或创建：

- PostgreSQL：`127.0.0.1:15432`
- RustFS API：`http://127.0.0.1:9000`
- RustFS 控制台：`http://127.0.0.1:9001`

RustFS 登录信息：

```text
账号：rustfsadmin
密码：rustfsadmin
```

如果想手动执行 Docker 命令，可参考：

```powershell
docker run -d --name ragent-postgres `
  -e POSTGRES_DB=ragent `
  -e POSTGRES_USER=postgres `
  -e POSTGRES_PASSWORD=123456 `
  -p 15432:5432 `
  -v ragent-pgdata:/var/lib/postgresql/data `
  registry-1.docker.io/pgvector/pgvector:pg16

docker run -d --name ragent-rustfs `
  -p 9000:9000 `
  -p 9001:9001 `
  -v ragent-rustfs-data:/data `
  -e RUSTFS_ACCESS_KEY=rustfsadmin `
  -e RUSTFS_SECRET_KEY=rustfsadmin `
  -e RUSTFS_CONSOLE_ENABLE=true `
  registry-1.docker.io/rustfs/rustfs:1.0.0-alpha.72 `
  --address :9000 `
  --console-enable `
  --access-key rustfsadmin `
  --secret-key rustfsadmin `
  /data
```

## 4. 初始化 PostgreSQL

首次启动数据库后执行：

```powershell
cd D:\1-project\ragent
.\scripts\windows\init-postgres.ps1
```

初始化顺序固定为：

1. `resources/database/schema_pg.sql`
2. `resources/database/init_data_pg.sql`

检查数据库表：

```powershell
docker exec ragent-postgres psql -U postgres -d ragent -c "\dt"
docker exec ragent-postgres psql -U postgres -d ragent -c "select count(*) from t_knowledge_base;"
```

## 5. 配置公共云连接

配置文件：

```text
D:\1-project\ragent\bootstrap\src\main\resources\application-public-cloud.yaml
```

当前关键配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: ${RAGENT_NACOS_SERVER_ADDR:common-nacos-dev.magestack.cn:8848}

  data:
    redis:
      host: ${RAGENT_REDIS_HOST:common-redis-dev.magestack.cn}
      port: ${RAGENT_REDIS_PORT:19389}
      password: ${RAGENT_REDIS_PASSWORD:<教程提供的Redis密码>}

rocketmq:
  name-server: ${RAGENT_ROCKETMQ_NAME_SERVER:common-rocketmq-dev.magestack.cn:9876}

unique-name: ${RAGENT_UNIQUE_NAME:-dingma23a45}

framework:
  cache:
    redis:
      prefix: "${RAGENT_REDIS_PREFIX:dingma23a45:}"
```

也可以用环境变量覆盖：

```powershell
$env:RAGENT_REDIS_HOST="common-redis-dev.magestack.cn"
$env:RAGENT_REDIS_PORT="19389"
$env:RAGENT_REDIS_PASSWORD="<教程提供的Redis密码>"
$env:RAGENT_ROCKETMQ_NAME_SERVER="common-rocketmq-dev.magestack.cn:9876"
$env:RAGENT_UNIQUE_NAME="-dingma23a45"
$env:RAGENT_REDIS_PREFIX="dingma23a45:"
```

注意：RocketMQ 控制台地址不能作为 NameServer 使用。NameServer 必须是：

```text
common-rocketmq-dev.magestack.cn:9876
```

## 6. 配置 AI Key

你已经配置了阿里云百炼和硅基流动 API Key。

推荐使用环境变量：

```powershell
$env:BAILIAN_API_KEY="<你的百炼API Key>"
$env:SILICONFLOW_API_KEY="<你的硅基流动API Key>"
```

如果已经写入 `application.yaml`，可以不再设置环境变量。不要把真实 Key 提交到公共仓库。

## 7. 启动后端

### 方式 A：IDEA 启动，推荐

使用 IDEA 打开项目：

```text
D:\1-project\ragent
```

确认：

- Project SDK 为 JDK 17+
- Maven 依赖已导入成功

启动运行配置：

```text
RagentApplication-public-cloud
```

该配置等价于：

```text
active profile: public-cloud
VM options: -Dunique-name=-dingma23a45 -Dframework.cache.redis.prefix=dingma23a45:
```

启动成功后后端地址：

```text
http://127.0.0.1:9090/api/ragent
```

### 方式 B：Docker 启动后端，无需本机 JAVA_HOME

如果 PowerShell 中没有 `java`、`mvn`，可以用 Docker 中的 JDK 启动：

```powershell
cd D:\1-project\ragent

docker run --rm -v "${PWD}:/workspace" -w /workspace `
  registry-1.docker.io/library/maven:3.9.9-eclipse-temurin-17 `
  mvn -pl bootstrap -am package "-DskipTests" "-Dspotless.apply.skip=true" "-Dspotless.check.skip=true"
```

启动容器：

```powershell
docker rm -f ragent-backend

docker run -d --name ragent-backend `
  -p 9090:9090 `
  -v "${PWD}:/workspace" `
  -w /workspace `
  -e RAGENT_POSTGRES_URL="jdbc:postgresql://host.docker.internal:15432/ragent?client_encoding=UTF8" `
  -e RAGENT_RUSTFS_URL="http://host.docker.internal:9000" `
  registry-1.docker.io/library/maven:3.9.9-eclipse-temurin-17 `
  sh -lc "java -Dspring.profiles.active=public-cloud -Dunique-name=-dingma23a45 -Dframework.cache.redis.prefix=dingma23a45: -Drag.mcp.servers[0].url=http://host.docker.internal:9099 -jar bootstrap/target/bootstrap-0.0.1-SNAPSHOT.jar"
```

查看日志：

```powershell
docker logs -f ragent-backend
```

## 8. 启动 MCP 服务

如果需要 MCP 工具能力，启动：

```text
MCPServerApplication-public-cloud
```

本地地址：

```text
http://127.0.0.1:9099
```

后端启动日志里看到类似下面内容，说明 MCP 已连接：

```text
MCP Server 返回 3 个工具
MCP 工具注册成功, toolId: sales_query
MCP 工具注册成功, toolId: ticket_query
MCP 工具注册成功, toolId: weather_query
```

## 9. 启动前端

进入前端目录：

```powershell
cd D:\1-project\ragent\frontend
npm install
npm run dev
```

前端地址：

```text
http://127.0.0.1:5173/
```

默认登录：

```text
账号：admin
密码：admin
```

## 10. 启动后检查

检查容器：

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

检查端口连通性：

```powershell
cd D:\1-project\ragent
.\scripts\windows\verify-ragent-public-cloud-env.ps1
```

检查后端：

```powershell
Invoke-WebRequest -Uri "http://127.0.0.1:9090/api/ragent/" -UseBasicParsing
```

未登录时返回 `未登录或登录已过期` 也说明后端服务已启动。

登录接口测试：

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://127.0.0.1:9090/api/ragent/auth/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}'
```

检查知识库和分块状态：

```powershell
docker exec ragent-postgres psql -U postgres -d ragent -c "select id,name,collection_name,deleted from t_knowledge_base order by create_time desc;"

docker exec ragent-postgres psql -U postgres -d ragent -c "select id,doc_name,status,chunk_count,update_time from t_knowledge_document order by create_time desc;"

docker exec ragent-postgres psql -U postgres -d ragent -c "select count(*) as chunks from t_knowledge_chunk; select count(*) as vectors from t_knowledge_vector;"
```

## 11. 需要查看的链接

### 本地前端

```text
http://127.0.0.1:5173/
```

用途：访问 Ragent 页面、管理后台、知识库管理、上传文档、问答测试。

### 本地后端

```text
http://127.0.0.1:9090/api/ragent
```

用途：后端 API 地址。前端请求会转发到这里。

### RustFS 控制台

```text
http://127.0.0.1:9001
```

用途：查看对象存储 bucket 和上传文件。新建知识库会创建同名 bucket。

登录：

```text
rustfsadmin / rustfsadmin
```

### RustFS API

```text
http://127.0.0.1:9000
```

用途：后端实际读写文件的 S3 兼容 API 地址。

### RocketMQ 控制台

```text
http://common-rocketmq-dev.magestack.cn:8088
```

用途：查看公共云 RocketMQ 的 topic、consumer group、消息堆积。

当前应重点查看：

```text
Topic: knowledge-document-chunk_topic-dingma23a45
Consumer Group: knowledge-document-chunk_cg-dingma23a45

Topic: message-feedback_topic-dingma23a45
Consumer Group: message-feedback_cg-dingma23a45
```

如果看到消息堆积长期不为 0，说明消费者可能没启动或消费失败。

### RocketMQ NameServer

```text
common-rocketmq-dev.magestack.cn:9876
```

用途：后端配置项，不是浏览器页面。

### Nacos

```text
http://common-nacos-dev.magestack.cn:8848/nacos
```

用途：查看服务注册情况。如果教程提供账号密码，可以登录控制台查看。当前项目主要通过配置连接，不一定必须手动查看。

### Redis

```text
common-redis-dev.magestack.cn:19389
```

用途：后端缓存、限流、队列状态等。通常没有 Web 控制台，主要通过后端日志确认连接成功。

后端日志看到类似内容说明 Redis 已连接：

```text
Redisson
connections initialized for common-redis-dev.magestack.cn:19389
```

## 12. 常见问题

### 新建知识库提示系统执行出错

常见原因：

- `collection_name` 已存在
- 之前删除知识库只是软删除，数据库唯一索引仍占用名称
- RustFS 中已有同名 bucket

排查：

```powershell
docker exec ragent-postgres psql -U postgres -d ragent -c "select id,name,collection_name,deleted from t_knowledge_base where collection_name='product';"
```

如果是测试空数据，可以清理软删除记录和空 bucket；正式数据不要直接删。

### 上传文档后一直 running

先查数据库：

```powershell
docker exec ragent-postgres psql -U postgres -d ragent -c "select id,doc_name,status,chunk_count,update_time from t_knowledge_document order by create_time desc;"

docker exec ragent-postgres psql -U postgres -d ragent -c "select doc_id,status,chunk_count,error_message,start_time,end_time,total_duration from t_knowledge_document_chunk_log order by create_time desc;"
```

再查消费者日志：

```powershell
docker logs --since 10m ragent-backend | Select-String -Pattern "开始消费文档分块|批量写入向量|文档分块任务执行失败|ERROR"
```

正常应该看到：

```text
[消费者] 开始消费文档分块任务
批量写入向量到 PostgreSQL
```

如果没有消费日志，而 RocketMQ 发送成功，多半是 `unique-name` 和别人共用了。换成自己的 `unique-name` 和 Redis prefix 后，重启后端再重新触发分块。

### 查看 RocketMQ 消费堆积

如果后端容器里已经下载过 RocketMQ 工具，可以执行：

```powershell
docker exec ragent-backend sh -lc 'CP=$(find /root/.m2/repository -name "*.jar" | tr "\n" ":"); java -cp "$CP" org.apache.rocketmq.tools.command.MQAdminStartup consumerProgress -n common-rocketmq-dev.magestack.cn:9876 -g knowledge-document-chunk_cg-dingma23a45'
```

重点看：

```text
Consume Diff Total
Inflight
LastTime
```

`Consume Diff Total` 长期大于 0 表示有堆积。

### PowerShell 中 java/mvn 不可用

IDEA 可以独立配置 JDK，所以不一定影响 IDEA 启动。

如果要在 PowerShell 直接执行 Maven，需要配置：

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

也可以使用 Docker Maven 镜像构建，避免本机配置 `JAVA_HOME`。

## 13. 推荐日常启动顺序

每天开发时按这个顺序：

```powershell
cd D:\1-project\ragent
.\scripts\windows\start-ragent-required-middleware.ps1
.\scripts\windows\verify-ragent-public-cloud-env.ps1
```

然后在 IDEA 启动：

```text
RagentApplication-public-cloud
MCPServerApplication-public-cloud
```

最后启动前端：

```powershell
cd D:\1-project\ragent\frontend
npm run dev
```

访问：

```text
http://127.0.0.1:5173/
```
