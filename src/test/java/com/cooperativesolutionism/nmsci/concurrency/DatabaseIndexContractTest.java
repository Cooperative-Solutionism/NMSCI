package com.cooperativesolutionism.nmsci.concurrency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIndexContractTest {

    private static final Path MIGRATION_DIR = Path.of("src/main/resources/db/migration");

    private static final List<String> REQUIRED_INDEXES = List.of(
            "idx_flow_node_register_pubkey",
            "idx_flow_node_locked_pubkey",
            "idx_central_pubkey_locked_pubkey",
            "idx_central_pubkey_empower_flow_central",
            "idx_transaction_record_consume_pubkey",
            "idx_transaction_record_flow_pubkey",
            "idx_transaction_record_consume_flow_pubkey",
            "idx_transaction_mount_consume_pubkey",
            "idx_transaction_mount_flow_pubkey",
            "idx_transaction_mount_consume_flow_pubkey",
            // 性能审计 QW1（V5）：交易查询按 confirm_timestamp 范围过滤 + (confirm_timestamp desc, id desc) 排序的支撑索引
            "idx_transaction_record_consume_confirm_ts",
            "idx_transaction_record_flow_confirm_ts",
            "idx_transaction_record_confirm_ts",
            "idx_transaction_mount_consume_confirm_ts",
            "idx_transaction_mount_flow_confirm_ts",
            "idx_transaction_mount_confirm_ts",
            "idx_msg_abstracts_unblocked_timestamp",
            "idx_msg_abstracts_unblocked_timestamp_id",
            "idx_block_infos_height_desc",
            "idx_consume_chains_open_end_currency_tail",
            "idx_consume_chains_start",
            "idx_consume_chains_start_loop",
            "idx_consume_chains_end",
            "idx_consume_chains_end_loop",
            "idx_consume_chain_edges_chain_timestamp",
            "idx_consume_chain_edges_mount",
            "idx_consume_chain_edges_source_target_currency_time",
            "idx_consume_chain_edges_target_currency_time"
    );

    @Test
    void schemaCreatesAllQueryPerformanceIndexesForNewDatabases() throws IOException {
        String migrationSql = readAllMigrations();

        for (String indexName : REQUIRED_INDEXES) {
            assertTrue(
                    migrationSql.contains("create index " + indexName),
                    () -> "db/migration must create " + indexName
            );
        }
    }

    private static String readAllMigrations() throws IOException {
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .map(DatabaseIndexContractTest::readString)
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String readString(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
