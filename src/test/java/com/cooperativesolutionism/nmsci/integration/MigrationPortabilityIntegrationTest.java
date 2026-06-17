package com.cooperativesolutionism.nmsci.integration;

import com.cooperativesolutionism.nmsci.support.DockerAvailableCondition;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迁移可移植性：基线迁移必须在「非 postgres 角色」下也能成功。
 *
 * <p>历史上 {@code V1__baseline.sql} 对每张表硬编码 {@code alter table ... owner to postgres}，
 * 只有当连接角色恰好名为 {@code postgres}（如集成测试容器）时才不报错；任何其它角色都会因
 * {@code role "postgres" does not exist} 而迁移失败。本测试以非 postgres 角色运行整套迁移，
 * 既验证可移植性，也作为该回归的护栏。
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
class MigrationPortabilityIntegrationTest {

    private static final String NON_POSTGRES_ROLE = "nmsci_app";

    @Test
    void baselineMigrationSucceedsUnderNonPostgresRole() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("nmsci_portability")
                .withUsername(NON_POSTGRES_ROLE)
                .withPassword(NON_POSTGRES_ROLE)) {
            postgres.start();

            MigrateResult result = Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load()
                    .migrate();

            assertTrue(result.success, "迁移应成功完成");
            assertTrue(result.migrationsExecuted >= 1, "至少应执行一条迁移");

            // 迁移产物归属连接角色（非 postgres），证明已无对 postgres 角色的硬编码依赖
            try (Connection connection = DriverManager.getConnection(
                    postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                 PreparedStatement statement = connection.prepareStatement(
                         "select tableowner from pg_tables where schemaname = 'public' and tablename = ?")) {
                statement.setString(1, "block_infos");
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertTrue(resultSet.next(), "block_infos 表应已创建");
                    assertEquals(NON_POSTGRES_ROLE, resultSet.getString("tableowner"));
                }
            }
        }
    }
}
