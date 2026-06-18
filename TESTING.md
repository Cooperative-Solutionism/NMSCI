# 测试说明

本项目使用 JUnit Jupiter、Spring Boot Test、Mockito、Testcontainers 和 Gatling。Maven 将测试分为三类：

- 快速单元测试和契约测试：不依赖 Docker，在 Maven `test` 阶段由 Surefire 运行。
- Docker 集成测试：继承 `NmsciIntegrationTestBase` 或显式使用 `DockerAvailableCondition`，通过 Testcontainers 启动 PostgreSQL，并带有 `integration` 标签，在 Maven `integration-test` 和 `verify` 阶段由 Failsafe 运行。
- Gatling 压力测试：位于 `src/test/java/com/cooperativesolutionism/nmsci/stress/FullLifecycleSimulation.java`，对运行中的服务施压，不绑定到 `test` / `verify` 生命周期。

Surefire 与 Failsafe 都通过 `-javaagent:${org.mockito:mockito-core:jar} -Xshare:off` 静态加载 Mockito，避免 JDK 21 动态挂载 agent 的弃用告警。

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

运行 Gatling 压测：

```bash
./mvnw gatling:test -Dgatling.baseUrl=http://localhost:8080 -Dgatling.users=50 -Dgatling.duration=60
```

压测前需要先按 README「压力测试（Gatling）」章节启动 `load` profile 服务和独立 PostgreSQL。

## Maven 分层

| 阶段/插件 | 覆盖范围 | Docker 需求 | 报告目录 |
| --- | --- | --- | --- |
| `test` / Surefire | 未标记 `integration` 的 `*Test` | 否 | `target/surefire-reports/` |
| `integration-test`、`verify` / Failsafe | 标记 `integration` 的 `*Test` | 是 | `target/failsafe-reports/` |
| `gatling:test` | `FullLifecycleSimulation` | 被压环境需要 PostgreSQL | `target/gatling/` |

集成测试运行时会注入 `spring.profiles.active=test`、禁用调度任务、使用 Testcontainers PostgreSQL，并把区块/源码包测试文件写入 `target/nmsci-test-files`。

默认 Docker API 版本由 `pom.xml` 的 `docker.api.version=1.40` 注入给 Testcontainers；如本地 Docker 环境需要不同版本，可用 Maven 系统属性覆盖。

```bash
./mvnw verify -Ddocker.api.version=1.43
```

## CI

CI 执行：

```bash
./mvnw -B clean verify
```

因此 CI 会运行快速测试和 Docker 集成测试。

CI 在 `mvn verify` 之前有一道 `docker version` 硬校验：若运行器上 Docker 不可用即直接失败，避免 Testcontainers 集成测试被静默跳过而让构建「假绿」。本地确实没有 Docker 时，应显式用 `-Dnmsci.integration-tests.enabled=false` 跳过，而非依赖静默跳过。
