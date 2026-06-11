package com.cooperativesolutionism.nmsci.concurrency;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseIndexContractTest {

    private static final Path SCHEMA_SQL = Path.of("src/main/resources/database/schema.sql");

    private static final Path INDEX_PATCH_SQL = Path.of(
            "src/main/resources/database/patches/2026-06-11-add-query-performance-indexes.sql"
    );

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
            "idx_msg_abstracts_unblocked_timestamp",
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
        String schemaSql = Files.readString(SCHEMA_SQL);

        for (String indexName : REQUIRED_INDEXES) {
            assertTrue(
                    schemaSql.contains("create index " + indexName),
                    () -> "schema.sql must create " + indexName
            );
        }
    }

    @Test
    void patchCreatesAllQueryPerformanceIndexesForExistingDatabases() throws IOException {
        String patchSql = Files.readString(INDEX_PATCH_SQL);

        for (String indexName : REQUIRED_INDEXES) {
            assertTrue(
                    patchSql.contains("create index if not exists " + indexName),
                    () -> "index patch must create " + indexName + " idempotently"
            );
        }
    }
}
