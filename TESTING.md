# 测试说明

本项目使用 JUnit Jupiter，并将测试分为两类：

- 快速单元测试和契约测试：不依赖 Docker，在 Maven `test` 阶段运行。
- Docker 集成测试：继承 `NmsciIntegrationTestBase`，通过 Testcontainers 启动 PostgreSQL，并带有 `integration` 标签，在 Maven `integration-test` 和 `verify` 阶段由 Failsafe 运行。

## 常用命令

运行快速测试：

```bash
./mvnw test
```

运行全量测试：

```bash
./mvnw verify
```

全量测试需要 Docker 可用。集成测试还受 `nmsci.integration-tests.enabled` 系统属性控制；需要临时禁用集成测试时使用：

```bash
./mvnw verify -Dnmsci.integration-tests.enabled=false
```

只运行单个快速测试类：

```bash
./mvnw -Dtest=Sha256UtilTest test
```

只运行单个集成测试类：

```bash
./mvnw test-compile -Dit.test=ProtocolErrorIntegrationTest failsafe:integration-test
```

## CI

CI 执行：

```bash
./mvnw -B clean verify
```

因此 CI 会运行快速测试和 Docker 集成测试。
