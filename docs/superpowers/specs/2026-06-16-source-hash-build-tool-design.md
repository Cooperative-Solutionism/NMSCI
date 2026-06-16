# 源码哈希构建工具硬化设计

## 背景

`docs/code-quality-audit-status.md` 将「源码哈希失败兜底 + 排除集对齐」列为下一轮最高优先级。当前 `CalcSourceCodeZipHash` 通过手写排除集遍历工作目录生成 `source_code_v*.zip`，并在 `main` 中吞掉异常，只打印错误但不以非 0 退出。这会带来两个问题：

- 源码 zip 文件集与 `.gitignore` / `.dockerignore` / CI 干净检出不完全一致，可能把本地或历史文件错误纳入链上源码哈希。
- 构建期生成 zip、计算 hash、写入 properties 失败时，Maven 仍可能继续，导致产物携带陈旧或占位的 `nmsci.source-code-zip-hash`。

本轮选择定向硬化构建工具，不引入大范围结构重构。

## 目标

- 源码 zip 的文件集以 `git ls-files -z` 输出的已跟踪文件为准。
- zip 内容读取当前工作树文件内容；已跟踪但未提交的本地修改会进入 zip。
- 未跟踪文件、被忽略文件、构建产物、本地环境文件不会进入 zip，因为它们不在 `git ls-files` 输出中。
- 历史生成物 `src/main/resources/static/source_code_v*.zip` 即使被误跟踪，也必须显式排除，避免 zip 自包含导致 hash 不稳定。
- 任一关键步骤失败时，构建失败：Git 不可用、`git ls-files` 非 0、文件读取失败、zip 写入失败、hash 计算失败、properties 读取/写入失败。
- 保留 deterministic zip：条目名统一 `/`，按条目名排序，固定时间戳。

## 非目标

- 不要求工作树干净。
- 不读取 Git HEAD blob；源码内容来自当前文件系统。
- 不新增跳过源码 hash 的 Maven 开关。
- 不调整 `nmsci.source-code-zip-hash` 的运行时配置语义。
- 不改区块生成、API、并发测试、Secp256k1 原语测试或结构性重构项。

## 设计方案

继续保留 `CalcSourceCodeZipHash` 单类实现，只在类内拆出小方法，避免过度重构：

- `trackedFiles(Path root)`：执行 `git -C <root> ls-files -z`，解析 NUL 分隔输出，返回相对路径列表。
- `shouldIncludeTrackedFile(Path relativePath)`：对 Git 返回的已跟踪文件做少量二次过滤，主要排除 `src/main/resources/static/source_code_v*.zip`。
- `zipTrackedFiles(Path root, List<Path> files, Path zipPath)`：按 zip entry name 排序写入 zip；entry name 使用 `/`；entry timestamp 固定；内容读取 `root.resolve(relativePath)`。
- `calcFileHash(Path zipPath)`：继续计算 SHA-256，可顺手改为缓冲读取，降低构建期 IO 成本。
- `run(Path root)`：串联删除旧 zip、读取 `target/classes/application.properties`、解析 `nmsci.block-version`、生成 zip、计算 hash、写回 `nmsci.source-code-zip-hash`。
- `main(String[] args)`：调用 `run`；失败时输出明确阶段信息并 `System.exit(1)`。

数据流：

```text
git ls-files -z
  -> 排除历史 source_code_v*.zip
  -> 按 zip entry name 排序
  -> 从当前工作树读取文件内容
  -> 生成 deterministic zip
  -> SHA-256
  -> 写入 target/classes/application.properties
```

## 错误处理

- `git ls-files` 无法启动、返回非 0、或没有输出可用文件时，抛出构建异常。
- 已跟踪文件在 zip 写入时不存在或不可读，抛出异常并中止构建。
- 清理旧 `source_code_v*.zip` 失败时中止构建，避免旧产物残留掩盖真实结果。
- `target/classes/application.properties` 不存在、缺少 `nmsci.block-version`、或无法写入时中止构建。
- `main` 捕获异常后打印包含阶段信息的错误日志，并以非 0 退出，确保 Maven antrun 失败。

## 测试策略

更新 `CalcSourceCodeZipHashContractTest`，使用临时目录初始化 Git repo，覆盖以下契约：

- 已跟踪源码、`pom.xml`、配置文件会进入 zip。
- 未跟踪文件不会进入 zip。
- `.gitignore` 忽略的文件不会进入 zip。
- 已跟踪但未提交修改的文件内容会进入 zip。
- 即使历史 `src/main/resources/static/source_code_v1.zip` 被 Git 跟踪，也不会进入 zip。
- 非 Git 目录或 `git ls-files` 失败会让构建工具抛错。
- `run` 会生成 zip、计算 hash，并把 hash 写入临时 `target/classes/application.properties`。
- 继续验证 zip entry 使用 `/`、按序输出、固定时间戳、重复运行字节一致。

验收命令：

```powershell
.\mvnw.cmd -Dtest=CalcSourceCodeZipHashContractTest test
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` 仍需要 Docker/Testcontainers 可用。

## 风险与权衡

- 读取当前工作树内容意味着未提交但已跟踪的改动会进入源码 zip。这符合本轮选择，适合本地 Maven 构建；它不保证 hash 对应某个 Git commit。
- 使用 `git ls-files` 使构建依赖 Git CLI。若未来要支持无 `.git` 的源码发行包构建，需要另行设计 fallback；本轮不做。
- 排除历史 `source_code_v*.zip` 是必要的二次过滤，避免源码包自包含。其余排除规则应尽量由 Git 跟踪状态表达，减少手写排除集漂移。
