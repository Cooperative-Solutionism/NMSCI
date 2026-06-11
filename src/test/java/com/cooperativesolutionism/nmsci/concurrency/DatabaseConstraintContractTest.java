package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import org.hibernate.annotations.Check;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConstraintContractTest {

    private static final String TRANSACTION_AMOUNT_CONSTRAINT = "ck_transaction_record_msgs_amount_positive";
    private static final String TRANSACTION_AMOUNT_CHECK = "amount > 0";
    private static final Path SCHEMA_SQL = Path.of("src/main/resources/database/schema.sql");
    private static final Path AMOUNT_PATCH_SQL = Path.of(
            "src/main/resources/database/patches/2026-06-12-add-positive-transaction-amount-check.sql"
    );

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
    void patchRejectsNonPositiveTransactionRecordAmountsForExistingDatabases() throws IOException {
        String patchSql = Files.readString(AMOUNT_PATCH_SQL);

        assertTrue(patchSql.contains("add constraint " + TRANSACTION_AMOUNT_CONSTRAINT));
        assertTrue(patchSql.contains("check (" + TRANSACTION_AMOUNT_CHECK + ")"));
    }
}
