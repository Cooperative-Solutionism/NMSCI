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
- 构建时生成当前源码压缩包并写入源码包哈希。

## 技术栈

- Java 17
- Spring Boot 3.5
- Spring Web
- Spring Data JPA
- PostgreSQL
- Maven Wrapper
- Testcontainers

## 环境要求

- JDK 17 或更高版本。
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
   GET /block-chain/last
   ```

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

常用入口：

```text
GET  /block-chain/last
GET  /block-chain/height/{height}
GET  /block-chain/hash/{hash}
GET  /system/params
GET  /metadata/msg-types
GET  /metadata/currency-types
GET  /flow-node/list
GET  /consume-chain/by-id
GET  /consume-chain/by-pubkey
GET  /returning-flow-rate/by-id
GET  /returning-flow-rate/by-pubkey
POST /flow-node-register-msg
POST /central-pubkey-empower-msg
POST /transaction-record-msg
POST /transaction-mount-msg
```

静态文件入口：

```text
/dat/**
/source-code/**
```

## 项目结构

```text
src/main/java/com/cooperativesolutionism/nmsci
├── controller      HTTP API
├── converter       协议字节与模型转换
├── dto             请求和响应 DTO
├── enumeration     协议枚举
├── exception       全局异常处理
├── model           JPA 实体
├── repository      Spring Data Repository
├── service         业务服务实现
├── service/impl    持久化辅助类
├── task            定时任务
└── util            字节、哈希、签名、PoW 等工具
```

数据库脚本：

```text
src/main/resources/db/migration/
```

## 参考资料

- [消费意愿数值化衡量系统协议规范](./PROTOCOL.md)
- [自证的制度：金钱的运转办法，经济合作解主义](https://www.bilibili.com/video/BV13Z421p79c)
