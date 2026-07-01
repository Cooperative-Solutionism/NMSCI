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
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 生产升级路径护栏：线上 v1.0.0 库是「非空、无 flyway_schema_history」的 V1 形态（由 Hibernate 自动建表，
 * 与 V1__baseline.sql 等价）。用 baseline-on-migrate + baseline-version=1 启动时，Flyway 必须把它自动基线到
 * V1（不重跑建表），随后只执行 V2/V3/V4，并把存量库收敛到与全新库一致的最终模式。
 *
 * <p>本测试复刻该场景：先用 Flyway 仅应用 V1 造出 V1 形态，删除 flyway_schema_history 模拟「无 Flyway 历史的线上库」，
 * 再以应用真实的 baseline 配置迁移，断言基线行为、V2/V3/V4 落地与 V4 轮换效果。集成测试用一次性容器，
 * 不影响「Flyway 从未在持久库运行过」这一前提。
 */
@Tag("integration")
@ExtendWith(DockerAvailableCondition.class)
class BaselineUpgradeFromLegacySchemaIntegrationTest {

    @Test
    void legacySchemaWithoutHistoryIsBaselinedAtV1ThenUpgraded() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("nmsci_baseline_upgrade")) {
            postgres.start();

            String url = postgres.getJdbcUrl();
            String user = postgres.getUsername();
            String password = postgres.getPassword();

            // 1) 造出「线上 v1.0.0」形态：仅应用 V1（表 + 主键 + 外键），再删除 Flyway 历史表，模拟无 Flyway 的存量库。
            Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .target("1")
                    .load()
                    .migrate();
            try (Connection connection = DriverManager.getConnection(url, user, password);
                 Statement statement = connection.createStatement()) {
                statement.execute("drop table flyway_schema_history");
            }

            // 2) 以应用真实配置迁移：非空库 + 无历史 -> 基线到 V1（不重跑），仅执行 V2/V3/V4/V5。
            MigrateResult result = Flyway.configure()
                    .dataSource(url, user, password)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("1")
                    .load()
                    .migrate();

            assertTrue(result.success, "存量库基线升级应成功");
            assertEquals(4, result.migrationsExecuted, "仅应执行 V2/V3/V4/V5 四条迁移，V1 不应重跑");

            try (Connection connection = DriverManager.getConnection(url, user, password)) {
                // 历史表：V1 为 BASELINE，V2/V3/V4/V5 为 SQL 且全部成功
                assertEquals(1, count(connection,
                        "select count(*) from flyway_schema_history where version = '1' and type = 'BASELINE' and success = true"),
                        "V1 应被记为 BASELINE");
                assertEquals(4, count(connection,
                        "select count(*) from flyway_schema_history where version in ('2','3','4','5') and type = 'SQL' and success = true"),
                        "V2/V3/V4/V5 应全部作为 SQL 迁移成功");

                // V2 落地：补齐的唯一约束存在
                assertEquals(1, count(connection,
                        "select count(*) from pg_constraint where conname = 'uk_block_infos_height'"),
                        "V2 应补齐 uk_block_infos_height");
                assertEquals(1, count(connection,
                        "select count(*) from pg_constraint where conname = 'uk_transaction_record_msgs_txid'"),
                        "V2 应补齐 uk_transaction_record_msgs_txid");

                // V3 落地：补齐挂载外键
                assertEquals(1, count(connection,
                        "select count(*) from pg_constraint where conname = 'fk_transaction_mount_mounted_record'"),
                        "V3 应补齐 fk_transaction_mount_mounted_record");

                // V4 轮换效果：旧单列公证唯一约束被替换为组合唯一约束
                assertEquals(0, count(connection,
                        "select count(*) from pg_constraint where conname = 'uk_central_pubkey_empower_msgs_flow_node_pubkey'"),
                        "V4 应删除旧的单列公证唯一约束");
                assertEquals(1, count(connection,
                        "select count(*) from pg_constraint where conname = 'uk_central_pubkey_empower_flow_central'"),
                        "V4 应建立 (流转节点公钥, 中心公钥) 组合唯一约束");
            }
        }
    }

    private static long count(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertTrue(resultSet.next(), "计数查询应返回一行");
            return resultSet.getLong(1);
        }
    }
}
