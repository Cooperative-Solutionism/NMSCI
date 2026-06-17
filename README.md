# NMSCI

消费意愿数值化衡量系统（Numerical Measurement System for Consumption Intention）。

在未来，除了商品的价格和质量之外，商品生产者的消费意愿将成为人们购买商品需要着重考虑的因素之一。

协议规范见 [PROTOCOL.md](./PROTOCOL.md)。

## 开发声明

从 git `2525cf` 提交节点之后，项目开始使用 AI 辅助开发。

## 功能概览

- 流转节点注册与冻结。
- 中心公钥授权与冻结。
- 交易记录与交易挂载。
- 消费链生成与回流率查询。
- 定时生成区块，并写入 `.dat` 区块文件。
- 独立链验证器：仅凭落盘 `.dat` 重新解析并核验帧格式、哈希链、各区块中心签名、默克尔根与每条消息的 PoW/签名（端点 `GET /verify/chain`，离线 CLI `VerifyChainCli`）。
- 构建时生成当前源码压缩包并写入源码包哈希。
- REST 读侧查询：自然键（pubkey/txid 等）统一走查询参数，列表端点统一分页（Slice）。
- 运维与协议元数据端点：系统状态、存储用量、消息类型/货币类型/区块格式/难度目标。
- 可观测性：Micrometer 业务指标（出块耗时/大小/积压、消费链分配耗时等）+ 内置 HTTP/JVM/HikariCP 指标，经 `/actuator/prometheus` 暴露；拒绝/错误日志带请求端点上下文。

## 技术栈

- Java 21
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway（数据库迁移）
- Maven Wrapper
- Testcontainers

## 环境要求

- JDK 21 或更高版本。
- PostgreSQL。
- Docker。运行集成测试时需要 Testcontainers。
- Bash 或兼容 shell。Windows 环境建议使用 WSL 或 Git Bash 执行 Maven Wrapper。

## 配置

基础配置位于 [src/main/resources/application.properties](./src/main/resources/application.properties)。

常用配置项：

```properties
nmsci.block-version=1
nmsci.block-header-size=229
nmsci.block-max-size=1048576
nmsci.block-dat-max-size=134217728

nmsci.file-root-dir=file
nmsci.file-dat-dir=dat
nmsci.file-source-code-dir=source-code
```

生产环境配置位于 [src/main/resources/application-prod.properties](./src/main/resources/application-prod.properties)，需要通过环境变量提供数据库和中心密钥：

```bash
export DB_URL='jdbc:postgresql://localhost:5432/nmsci'
export DB_USERNAME='postgres'
export DB_PASSWORD='your-password'
export CENTRAL_KEY_PAIR_PUBKEY='base64-public-key'
export CENTRAL_KEY_PAIR_PRIKEY='base64-private-key'
```

上述环境变量的占位模板见 [.env.example](./.env.example)，复制为 `.env` 后填入真实值（`.env` 已被 `.gitignore` 忽略）。

`nmsci.source-code-zip-hash` 不应手工维护。它会在 Maven 打包阶段由 `CalcSourceCodeZipHash` 计算后写入构建产物中的 `application.properties`。

## 本地运行

开发配置 `application-dev.properties` 不再包含密钥。首次运行前，复制密钥模板并填入本地值：

```bash
cp application-local.properties.example application-local.properties
# 编辑 application-local.properties，填入 spring.datasource.password 与 nmsci.central-key-pair.pubkey/prikey
```

`application-local.properties` 已被 `.gitignore` 忽略，dev 配置会通过 `spring.config.import` 自动加载它。

启动本地数据库后，使用开发配置运行：

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

默认服务端口：

```text
http://localhost:8080
```

开发配置默认将区块文件和源码包写入：

```text
temp/dat
temp/source-code
```

## Docker 运行

镜像采用「多阶段从源码构建」：构建阶段用项目自带的 Maven Wrapper 从源码打包，因此任何人 `docker build` 都能复现出与 CI 一致的上链源码哈希（`nmsci.source-code-zip-hash`）；运行阶段仅含 JRE 21 与可执行 jar，并以非 root 用户运行。

> **复现性约定：** 构建期 `CalcSourceCodeZipHash` 通过 `git ls-files` 取被 git 跟踪的文件集，因此 Docker build context 会有意保留 `.git` 元数据，确保容器内能执行同样的枚举。生成的 `source_code_v*.zip` 仍不会包含 `.git` 元数据，因为 Git 不会把自身元数据作为被跟踪文件输出。[.dockerignore](./.dockerignore) 只应排除 `target`、`logs`、`temp`、`.worktrees/`、IDE 文件，以及未跟踪的 `application-local.properties`/`.env` 等本地噪声。**请勿在 `.dockerignore` 中排除任何被 git 跟踪的源码/配置文件**，否则 Docker 构建算出的源码哈希会与 CI 不一致。注意：`Dockerfile`、`docker-compose.yml` 等一旦提交即纳入源码哈希。

### 一键启动（含 PostgreSQL）

```bash
cp .env.example .env          # 填入数据库口令与中心密钥对（prod profile 必需）
docker compose up -d --build
```

服务监听 `http://localhost:8080`，健康检查 `GET /actuator/health`。区块文件与日志分别持久化到命名卷 `nmsci-data`（容器内 `/app/file`）、`nmsci-logs`（`/app/logs`），数据库数据在 `pgdata` 卷。

### 仅构建 / 运行镜像

```bash
docker build -t nmsci:1.0.0 .
docker run --rm -p 8080:8080 --env-file .env \
  -e DB_URL='jdbc:postgresql://<db-host>:5432/nmsci' \
  -v nmsci-data:/app/file -v nmsci-logs:/app/logs \
  nmsci:1.0.0
```

容器默认 `SPRING_PROFILES_ACTIVE=prod`；JVM 参数可经 `JAVA_TOOL_OPTIONS` 注入（如 `-e JAVA_TOOL_OPTIONS='-Xmx512m'`）。

## 测试

快速测试：

```bash
./mvnw test
```

全量测试：

```bash
./mvnw verify
```

集成测试使用 Testcontainers 启动 PostgreSQL，因此全量测试需要 Docker 可用。更多说明见 [TESTING.md](./TESTING.md)。

只运行单个测试类：

```bash
./mvnw -Dtest=Sha256UtilTest test
```

如果当前环境没有 Docker，可以临时禁用集成测试：

```bash
./mvnw verify -Dnmsci.integration-tests.enabled=false
```

## 压力测试（Gatling）

压测基于 Gatling（Java DSL），对**运行中的实例**施压，覆盖全生命周期写链路（注册 → 授权 → 交易记录 → 交易挂载）与查询读链路。它**不随 `mvn verify` / CI 运行**（插件未绑定生命周期），仅按需经 `mvn gatling:test` 触发。

被压实例需以 `load` profile 启动：trivial PoW 难度、独立的 `nmsci_load` 库、区块文件落在 `temp/load`；中心密钥经环境变量注入，其值必须等于压测脚本默认签名所用的测试中心密钥（见下）。

```bash
# 1) 压测用 PostgreSQL（一次性，用完即弃）
docker run --rm -e POSTGRES_DB=nmsci_load -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16-alpine

# 2) 导出测试中心密钥（与压测脚本 TestKeyPairs.CENTRAL 一致）
export CENTRAL_KEY_PAIR_PUBKEY='A9+yx3Fml7ugoSwhxDH4bUv+O1NrLsC38y5/l7vPsgy+'
export CENTRAL_KEY_PAIR_PRIKEY='BIVAd26jw2HNo0izjp8YSSf78cmGneReK0PmD48mRns='

# 3) 启动服务（空库启动即生成创世块）
./mvnw spring-boot:run -Dspring-boot.run.profiles=load
#    若 8080 被占用：导出 SERVER_PORT=8099 后再启动，并在第 4 步用对应 baseUrl

# 4) 另开终端运行压测
./mvnw gatling:test -Dgatling.baseUrl=http://localhost:8080 -Dgatling.users=50 -Dgatling.duration=60
```

可调参数（默认值见 `pom.xml` 的 `gatling.*` 属性，经插件 `jvmArgs` 透传到 fork 的 Gatling JVM；Maven 的 `-D` 默认不会传入 fork）：

- `gatling.baseUrl`：被压实例地址（默认 `http://localhost:8080`）
- `gatling.users`：注入的虚拟用户数（默认 50）
- `gatling.duration`：用户注入时长（秒，默认 30）
- `gatling.amount`：交易金额（默认 1200）

HTML 报告输出到 `target/gatling/fulllifecyclesimulation-<时间戳>/index.html`。脚本内置断言：失败请求数为 0、响应时间 p95 < 2000ms。

## 构建

标准构建：

```bash
./mvnw clean package
```

构建过程中，Maven 会在 `prepare-package` 阶段执行 `CalcSourceCodeZipHash`：

1. 删除构建输出目录中旧的 `source_code_v*.zip`。
2. 根据当前 `nmsci.block-version` 生成 `source_code_v{block-version}.zip`。
3. 计算源码包 SHA-256。
4. 将哈希写入构建产物中的 `nmsci.source-code-zip-hash`。

构建后可以检查产物：

```bash
jar tf target/nmsci-1.0.0.jar | grep 'source_code_v'
unzip -p target/nmsci-1.0.0.jar BOOT-INF/classes/application.properties \
  | grep -E 'nmsci.block-version|nmsci.source-code-zip-hash'
```

## 部署

生产环境启动示例：

```bash
java -jar target/nmsci-1.0.0.jar --spring.profiles.active=prod
```

部署时必须保留同一套数据库和文件目录。默认生产文件目录为：

```text
file/dat
file/source-code
```

这些目录保存历史区块 `.dat` 文件和各版本源码包，不应在升级时清空。

Flyway 会在应用启动时自动执行数据库迁移；已有生产数据库会按 V1 建立基线，新的空数据库会从 V1 初始化完整结构。

应用启动后，`GenerateBlockTask` 会立即执行一次区块生成任务，之后每 10 分钟执行一次。第一次运行还会持续生成区块，直到没有未入块消息。

## 区块版本升级与重部署流程

当协议或实现发生需要上链声明的变化时，应递增 `nmsci.block-version` 并重新部署。

1. 暂停发布窗口

   停止或暂停外部新消息写入，等待旧版本完成当前未入块消息处理。这样可以避免升级边界上的消息归属不清。

2. 修改区块版本号

   修改 [src/main/resources/application.properties](./src/main/resources/application.properties)：

   ```properties
   nmsci.block-version=2
   ```

   不要手工修改 `nmsci.source-code-zip-hash`。

3. 运行测试

   ```bash
   ./mvnw clean test
   ```

4. 构建新版本

   ```bash
   ./mvnw clean package
   ```

   构建产物应包含新版本源码包，例如：

   ```text
   BOOT-INF/classes/static/source_code_v2.zip
   ```

5. 核验 jar 内版本与源码包哈希

   ```bash
   unzip -p target/nmsci-1.0.0.jar BOOT-INF/classes/application.properties \
     | grep -E 'nmsci.block-version|nmsci.source-code-zip-hash'

   jar tf target/nmsci-1.0.0.jar | grep 'source_code_v2.zip'
   ```

   需要确认：

   - `nmsci.block-version` 是新版本号。
   - `nmsci.source-code-zip-hash` 不是全 0。
   - jar 内存在对应版本的 `source_code_v{version}.zip`。

6. 部署并启动新 jar

   ```bash
   java -jar target/nmsci-1.0.0.jar --spring.profiles.active=prod
   ```

   确保新版本继续使用原数据库和原 `nmsci.file-root-dir`。

7. 验证新区块

   启动后查询最新区块：

   ```text
   GET /blocks/latest
   ```

   > 全新空库在首个区块（创世块）生成前会返回 404；等待调度器产出首块后再查询。

   确认最新区块中的字段：

   - `version` 等于新 `nmsci.block-version`。
   - `sourceCodeZipHash` 等于新源码包哈希。
   - `sourceCodeZipFilepath` 为 `source_code_v{version}.zip`。

8. 恢复外部写入

   验证新区块正常生成后，再恢复外部消息写入。

注意事项：

- 每次协议或实现升级都应递增 `nmsci.block-version`，不要复用旧版本号。
- 历史区块仍引用旧版本源码包，新区块引用新版本源码包。
- `BlockChainServiceImpl` 只会在运行目录下不存在 `file/source-code/source_code_v{version}.zip` 时，从 jar 中复制源码包。
- 在 Linux、WSL 或 macOS 环境打包后，应额外抽查源码 zip 内容，确认没有把 `.git`、`target`、`temp`、`logs` 等目录打入源码包。

## API 入口

### 通用约定

- **响应封装**：除静态文件外，所有端点返回统一信封 `ResponseResult`：

  ```json
  { "code": 200, "message": "Success", "data": ... }
  ```

  成功 `code` 为 `200`；失败时错误详情统一放在 `message`，`data` 为 `null`。

- **分页**：列表/检索端点接受 `?page=&size=`（`page` 从 `0` 起，默认 `size=50`，上限 `200`），`data` 为 `SliceResponseDTO`：

  ```json
  { "content": [ ... ], "page": 0, "size": 50, "numberOfElements": 50, "hasNext": true, "hasPrevious": false }
  ```

  消息列表默认按中心确认时间戳倒序（`id` 倒序兜底）；流转节点注册信息无确认时间戳，按 `id` 升序。

- **二进制参数**：`flowNodePubkey`、`centralPubkey`、`hash` 等以 hex 字符串传入；时间戳（`startTime`/`endTime` 及实体内 `confirmTimestamp`）为微秒级（自 Unix 纪元，UTC）。同一查询不可混用 id 与 pubkey 两类定位参数，混用返回 `400`。

常用入口：

```text
# 区块
GET  /blocks/latest
GET  /blocks/{height}
GET  /blocks?hash={hash}

# 系统与运维
GET  /system/params
GET  /system/status
GET  /system/storage

# 链完整性验证（只读）
GET  /verify/chain            # 重新解析本地 .dat 并独立核验整条链；?stateful=false 可关闭有状态回放

# 元数据
GET  /metadata/message-types
GET  /metadata/currency-types
GET  /metadata/block-format
GET  /metadata/difficulty

# 流转节点
GET  /flow-nodes?registered=&authorized=&locked=
GET  /flow-nodes/{flowNodePubkey}

# 消费链
GET  /consume-chains/{id}
GET  /consume-chains?startId=|endId=|nodeId=|startPubkey=|endPubkey=|nodePubkey=|isLoop=|mountedTransactionId=
GET  /consume-chains/edges?targetId=|targetPubkey=(必填)&sourceId=|sourcePubkey=&currencyType=&startTime=&endTime=

# 回流率
GET  /returning-flow-rates?targetId=|targetPubkey=(必填)&sourceId=|sourcePubkey=&currencyType=&startTime=&endTime=

# 协议消息(写入 + 查询)
POST /flow-node-registrations
GET  /flow-node-registrations            GET /flow-node-registrations/{id}            (可选 ?flowNodePubkey=)
POST /flow-node-locks
GET  /flow-node-locks                     GET /flow-node-locks/{id}                     GET /flow-node-locks/status?flowNodePubkey=
POST /central-pubkey-empowerments
GET  /central-pubkey-empowerments         GET /central-pubkey-empowerments/{id}         (可选 ?flowNodePubkey=)
POST /central-pubkey-locks
GET  /central-pubkey-locks                GET /central-pubkey-locks/{id}                GET /central-pubkey-locks/status?centralPubkey=
POST /transaction-records
GET  /transaction-records                 GET /transaction-records/{id}                 (可选 ?consumeNodePubkey=&flowNodePubkey=&currencyType=&startTime=&endTime=)
POST /transaction-mounts
GET  /transaction-mounts                  GET /transaction-mounts/{id}                  (可选 ?consumeNodePubkey=&flowNodePubkey=&mountedTransactionRecordId=&startTime=&endTime=)
```

> `GET /metadata/message-types` 对每种消息类型返回两个字节数：`size` 为落库/上链最终字节数（含中心签名与确认时间戳），`inboundSize` 为入站 POST 请求体字节数（中心签名之前）。对不可中心签名的流转节点注册信息两者相等（均 123 字节），其余类型 `size` 比 `inboundSize` 多 72 字节（时间戳 8 + 中心签名 64）。详见 [PROTOCOL.md](./PROTOCOL.md)。

静态文件入口：

```text
/dat/**
/source-code/**
```

## 链完整性独立验证

链的防篡改与分发保障在于：任意第三方只凭分发出去的 `blk*.dat` 区块文件即可独立重建并核验整条链，无需信任本服务进程或数据库。`com.cooperativesolutionism.nmsci.verifier` 提供这一能力，所有哈希/签名/PoW 运算复用写入侧同一批工具类，保证与生成侧逐字节一致。

**核验项**

- 结构：`.dat` 帧格式（大端魔数 `0x6A466D85` + 长度前缀）、区块头/消息定长布局、区块版本、难度字段良构、消息内嵌类型与分段一致。
- 密码学：哈希链衔接（`前区块头摘要 == 上一区块 dblSHA256(完整区块头)`）、高度连续、区块中心签名（对区块头 [0,165) 验签）、默克尔根重算、最大消息时间戳。
- 单条消息：金额/币种、成员签名低 S、工作量证明、各方成员签名验证、中心签名验证。
- 有状态回放（可选，默认开启）：按 `confirmTimestamp` 重建**入账顺序**后重放全链，校验与时序强相关的前提——全链 id/注册/冻结唯一；公证未重复授权；引用的流转节点已注册；**消息发生时**中心公钥未冻结、流转节点未冻结、流转节点已对其中心公钥授权；挂载引用的交易记录存在且消费公钥一致。
- 入账时点规则（逐块）：消息难度=入账时最新区块（即前驱区块）难度；消息中心公钥=区块头中心公钥；中心公钥轮换仅当旧公钥已冻结时合法。
- 仅在锚定子链（首块非创世、其前驱不在本 .dat 内）时，首块的「难度=入账难度」「轮换合法性」无法独立判定而跳过；其余规则全覆盖。

**端点（运维自检）**：`GET /verify/chain`（见 [docs/API.md](./docs/API.md)）。每个区块在其自身头部公钥下验签，故对中心公钥轮换/版本升级稳健；不强制中心公钥/源码哈希等于本节点配置。

**离线 CLI（独立第三方，仅需 jar，无需数据库与运行中的服务）**：

```bash
java -Dloader.main=com.cooperativesolutionism.nmsci.verifier.VerifyChainCli \
     -cp target/nmsci-*.jar org.springframework.boot.loader.launch.PropertiesLauncher \
     <datDir> [--central-pubkey <hex|base64>] [--source-zip <path>|--source-hash <hex>] \
     [--starting-prev-hash <hex>] [--no-stateful]
# 退出码：0=通过，1=不通过，2=用法错误
```

`.dat` 首块非创世（如分发的是某段子链）时，用 `--starting-prev-hash` 提供该段应衔接的前区块 id 作为信任锚；`--central-pubkey` / `--source-zip` 提供带信任锚的更严格核验。

## 可观测性

指标经 Micrometer 暴露，抓取端点 `GET /actuator/prometheus`（另有 `GET /actuator/metrics/{name}` 查单项、`/actuator/health`、`/actuator/info`）。除内置的 `http.server.requests`（按端点+状态码的入账/拒绝/错误速率与时延直方图）、JVM、HikariCP 连接池等指标外，新增业务指标：

| 指标 | 类型 | 含义 |
| --- | --- | --- |
| `nmsci.block.generation` | Timer（含直方图） | 每次出块周期耗时 |
| `nmsci.block.generation.errors` | Counter | 出块失败次数 |
| `nmsci.block.size.bytes` | DistributionSummary | 每个区块原始字节数 |
| `nmsci.block.messages` | DistributionSummary | 每个区块消息条数 |
| `nmsci.block.height` | Gauge | 最新区块高度 |
| `nmsci.mempool.pending` | Gauge | 待入块消息积压数 |
| `nmsci.consumechain.allocation` | Timer（含直方图） | 消费链分配（交易挂载）耗时 |
| `nmsci.block.difficulty.lookup` | Timer | 最新区块难度查询时延 |

所有指标带 `application=nmsci` 公共标签。拒绝（4xx/409）与服务器错误（5xx）日志带 `方法 + 路径` 上下文，便于关联到具体端点。

> 生产部署注意：actuator 端点暴露在对外业务端口（8080）且无鉴权，仅开放 `health/info/metrics/prometheus`（不含 `env`/`configprops`/`beans`/`heapdump` 等敏感端点，无密钥泄露）。但 `/actuator/metrics`、`/actuator/prometheus` 会暴露运行态（积压、时延等），生产环境应通过防火墙/反向代理限制访问，或改用独立管理端口（`management.server.port` + `management.server.address=127.0.0.1`）。

## 项目结构

```text
src/main/java/com/cooperativesolutionism/nmsci
├── annotation         协议参数约束注解（@ByteArraySize）
├── block              区块装配、文件存储、消息选择
├── buildtool          构建期工具（源码包哈希计算，由 Maven antrun 于打包前调起）
├── config             Spring 配置类
├── config/properties  配置属性持有者（NmsciProperties）
├── constant           协议与系统常量
├── consume            消费链领域逻辑（分配、计划、环检测、持久化）
├── controller         HTTP API
├── converter          协议字节与模型转换
├── dto                请求和响应 DTO
├── enumeration        协议枚举
├── exception          全局异常处理与业务异常类型
├── model              JPA 实体与消息接口
├── protocol           协议校验器与编解码器（无状态，构建于 util 加密原语之上）
├── repository         Spring Data Repository
├── response           统一响应封装
├── serializer         自定义 Jackson 序列化器
├── service            业务服务、事务编排、查询编排
├── task               定时任务
├── util               字节、哈希、签名、PoW 等工具与加密原语
└── validator          协议参数约束校验器（@ByteArraySize 实现）
```

包边界约定：

- `consume` 持有消费链限界上下文的领域逻辑与其事务持久化；面向 HTTP 的编排入口 `ConsumeChain*Service` 放在 `service`。
- 需要独立复用的持久化编排助手统一命名 `{实体}PersistenceService`，与其所属业务/领域同包（如 `ConsumeChainPersistenceService` 在 `consume`）。中心公钥冻结流程由 `CentralPubkeyLockedMsgService` 直接通过 `TransactionTemplate` 持久化冻结消息与 `msg_abstracts`，随后再触发区块补齐与优雅停机请求。
- `protocol` 仅放无状态的协议校验与编解码，加密实现一律在 `util`。
- `buildtool` 是构建期工具，有意保留在源码树内，以保链上源码归档可自校验。

数据库脚本：

```text
src/main/resources/db/migration/
```

## 参考资料

- [消费意愿数值化衡量系统协议规范](./PROTOCOL.md)
- [自证的制度：金钱的运转办法，经济合作解主义](https://www.bilibili.com/video/BV13Z421p79c)
