# syntax=docker/dockerfile:1

# ============================================================================
# 构建阶段：用项目自带的 Maven Wrapper 从源码打包。
# 因为上链源码哈希（nmsci.source-code-zip-hash）在 prepare-package 阶段由
# CalcSourceCodeZipHash 对源码树计算，所以「从源码构建」可让任何人 docker build
# 复现出与 CI 一致的哈希。复现性依赖 .dockerignore 与该工具的排除项严格对齐。
# ============================================================================
# Maven 版本与 .mvn/wrapper/maven-wrapper.properties 中 wrapperVersion 一致（3.9.10），
# 直接用官方 maven 镜像避免构建期再下载 Maven 发行版（更稳）。源码哈希按源码文件计算，
# 故用 mvn 还是 ./mvnw 算出的哈希完全一致。
FROM maven:3.9.10-eclipse-temurin-21 AS build
WORKDIR /build

# 拷贝整个源码树（.dockerignore 已排除 .git/target/logs/temp/IDE 及未跟踪的本地文件，
# 使 /build 与 CI 干净检出一致）。单次 COPY 可避免中间态影响源码哈希。
COPY . .

# 用 BuildKit 缓存 ~/.m2（项目依赖），跳过测试（集成测试需 Docker，构建期不可用）。
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -DskipTests clean package

# ============================================================================
# 运行阶段：仅含 JRE 与可执行 jar，非 root 运行。
# alpine 自带 BusyBox wget（供健康检查用），无需 apt 安装；应用为纯 Java，musl 无虞。
# ============================================================================
FROM eclipse-temurin:21-jre-alpine AS runtime

# 非 root 用户
RUN addgroup -S nmsci && adduser -S -G nmsci -h /app nmsci

WORKDIR /app
COPY --from=build /build/target/nmsci-*.jar app.jar

# 区块文件（file/dat、file/source-code）与日志（logs/app.log）的可写目录；compose 中挂载为命名卷
RUN mkdir -p /app/file /app/logs && chown -R nmsci:nmsci /app
USER nmsci

# prod profile：数据库与中心密钥经环境变量注入（见 .env.example）
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
VOLUME ["/app/file", "/app/logs"]

# 启动后给足 Flyway 迁移与首次装块的时间
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8080/actuator/health || exit 1

# exec 形式：java 作为 PID 1 正确接收 SIGTERM（Spring Boot 优雅停机）；
# JVM 参数可经 JAVA_TOOL_OPTIONS 注入（JDK 21 默认启用容器内存感知）。
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
