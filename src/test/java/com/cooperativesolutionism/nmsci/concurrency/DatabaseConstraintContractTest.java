package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.model.BlockInfo;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConstraintContractTest {

    private static final String TRANSACTION_AMOUNT_CONSTRAINT = "ck_transaction_record_msgs_amount_positive";
    private static final String TRANSACTION_AMOUNT_CHECK = "amount > 0";
    private static final String BLOCK_HEIGHT_UNIQUE_CONSTRAINT = "uk_block_infos_height";
    private static final String BLOCK_PREVIOUS_HASH_UNIQUE_CONSTRAINT = "uk_block_infos_previous_block_hash";
    private static final UniqueConstraintSpec[] PROTOCOL_MESSAGE_UNIQUE_CONSTRAINTS = {
            new UniqueConstraintSpec(
                    FlowNodeRegisterMsg.class,
                    "uk_flow_node_register_msgs_flow_node_pubkey",
                    "flow_node_pubkey"
            ),
            new UniqueConstraintSpec(FlowNodeRegisterMsg.class, "uk_flow_node_register_msgs_txid", "txid"),
            new UniqueConstraintSpec(
                    CentralPubkeyEmpowerMsg.class,
                    "uk_central_pubkey_empower_msgs_flow_node_pubkey",
                    "flow_node_pubkey"
            ),
            new UniqueConstraintSpec(CentralPubkeyEmpowerMsg.class, "uk_central_pubkey_empower_msgs_txid", "txid"),
            new UniqueConstraintSpec(
                    CentralPubkeyLockedMsg.class,
                    "uk_central_pubkey_locked_msgs_central_pubkey",
                    "central_pubkey"
            ),
            new UniqueConstraintSpec(CentralPubkeyLockedMsg.class, "uk_central_pubkey_locked_msgs_txid", "txid"),
            new UniqueConstraintSpec(
                    FlowNodeLockedMsg.class,
                    "uk_flow_node_locked_msgs_flow_node_pubkey",
                    "flow_node_pubkey"
            ),
            new UniqueConstraintSpec(FlowNodeLockedMsg.class, "uk_flow_node_locked_msgs_txid", "txid"),
            new UniqueConstraintSpec(TransactionRecordMsg.class, "uk_transaction_record_msgs_txid", "txid"),
            new UniqueConstraintSpec(TransactionMountMsg.class, "uk_transaction_mount_msgs_txid", "txid")
    };
    private static final Path SCHEMA_SQL = Path.of("src/main/resources/db/migration/V1__baseline.sql");

    @Test
    void transactionRecordEntityDeclaresPositiveAmountCheckConstraint() {
        Check[] checks = TransactionRecordMsg.class.getAnnotationsByType(Check.class);

        assertTrue(
                Arrays.stream(checks).anyMatch(check -> TRANSACTION_AMOUNT_CONSTRAINT.equals(check.name())
                        && TRANSACTION_AMOUNT_CHECK.equals(check.constraints())),
                "TransactionRecordMsg amount must be protected by a named amount > 0 check constraint"
        );
    }

    @Test
    void schemaRejectsNonPositiveTransactionRecordAmountsForNewDatabases() throws IOException {
        String schemaSql = Files.readString(SCHEMA_SQL);

        assertTrue(schemaSql.contains("constraint " + TRANSACTION_AMOUNT_CONSTRAINT));
        assertTrue(schemaSql.contains("check (" + TRANSACTION_AMOUNT_CHECK + ")"));
    }

    @Test
    void blockInfoEntityDeclaresUniqueChainPositionConstraints() {
        Table table = BlockInfo.class.getAnnotation(Table.class);

        assertNotNull(table, "BlockInfo must declare table metadata");
        assertTrue(
                hasUniqueConstraint(table, "height"),
                "block height must be protected by a database unique constraint"
        );
        assertTrue(
                hasUniqueConstraint(table, "previous_block_hash"),
                "previous_block_hash must be unique to prevent two children for the same parent block"
        );
    }

    @Test
    void schemaPreventsDuplicateBlockHeightsAndParentForksForNewDatabases() throws IOException {
        String schemaSql = Files.readString(SCHEMA_SQL);

        assertTrue(schemaSql.contains("constraint " + BLOCK_HEIGHT_UNIQUE_CONSTRAINT));
        assertTrue(schemaSql.contains("unique (height)"));
        assertTrue(schemaSql.contains("constraint " + BLOCK_PREVIOUS_HASH_UNIQUE_CONSTRAINT));
        assertTrue(schemaSql.contains("unique (previous_block_hash)"));
    }

    @Test
    void protocolMessageEntitiesDeclareProtocolUniqueConstraints() {
        for (UniqueConstraintSpec spec : PROTOCOL_MESSAGE_UNIQUE_CONSTRAINTS) {
            Table table = spec.entityClass().getAnnotation(Table.class);

            assertNotNull(table, spec.entityClass().getSimpleName() + " must declare table metadata");
            assertTrue(
                    hasUniqueConstraint(table, spec.columnName()),
                    spec.entityClass().getSimpleName() + " must protect " + spec.columnName() + " with a database unique constraint"
            );
        }
    }

    @Test
    void schemaPreventsDuplicateProtocolMessagesForNewDatabases() throws IOException {
        String schemaSql = Files.readString(SCHEMA_SQL);

        for (UniqueConstraintSpec spec : PROTOCOL_MESSAGE_UNIQUE_CONSTRAINTS) {
            assertSqlContainsUniqueConstraint(schemaSql, spec);
        }
    }

    private boolean hasUniqueConstraint(Table table, String columnName) {
        return Arrays.stream(table.uniqueConstraints())
                .map(UniqueConstraint::columnNames)
                .anyMatch(columnNames -> Arrays.asList(columnNames).contains(columnName));
    }

    private void assertSqlContainsUniqueConstraint(String sql, UniqueConstraintSpec spec) {
        assertTrue(sql.contains("constraint " + spec.constraintName()));
        assertTrue(sql.contains("unique (" + spec.columnName() + ")"));
    }

    private record UniqueConstraintSpec(Class<?> entityClass, String constraintName, String columnName) {
    }
}
