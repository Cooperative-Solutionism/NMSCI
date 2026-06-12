package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceConcurrencyContractTest {

    @Test
    void transactionMountAllowsOnlyOneMountPerTransactionRecord() {
        Table table = TransactionMountMsg.class.getAnnotation(Table.class);

        assertNotNull(table, "TransactionMountMsg must declare table metadata");
        boolean hasMountedRecordUniqueConstraint = Arrays.stream(table.uniqueConstraints())
                .map(UniqueConstraint::columnNames)
                .anyMatch(columnNames -> Arrays.asList(columnNames).contains("mounted_transaction_record_id"));

        assertTrue(
                hasMountedRecordUniqueConstraint,
                "mounted_transaction_record_id must be protected by a database unique constraint"
        );
    }

    @Test
    void transactionRecordCanBeLockedBeforeMounting() throws NoSuchMethodException {
        Method method = TransactionRecordMsgRepository.class.getMethod("findByIdForUpdate", UUID.class);
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock, "findByIdForUpdate must use a pessimistic lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void openConsumeChainsAreReadWithWriteLockBeforeSplitOrExtension() throws NoSuchMethodException {
        Method method = ConsumeChainRepository.class.getMethod(
                "findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc",
                com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg.class,
                Short.class
        );
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock, "open consume chains must be selected with a pessimistic lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void openConsumeChainsCanBeLockedInBatchesBeforeAllocation() throws NoSuchMethodException {
        Method method = ConsumeChainRepository.class.getMethod(
                "findByIsLoopFalseAndEndAndCurrencyTypeOrderByTailMountTimestampAsc",
                com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg.class,
                Short.class,
                Pageable.class
        );
        Lock lock = method.getAnnotation(Lock.class);

        assertNotNull(lock, "batched open consume chain reads must use a pessimistic lock");
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }
}
