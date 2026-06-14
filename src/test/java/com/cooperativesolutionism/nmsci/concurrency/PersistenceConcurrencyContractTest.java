package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.service.TransactionMountMsgService;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

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

    @Test
    void messageSavesThatPersistMsgAbstractRunInOneTransaction() throws ReflectiveOperationException {
        assertTransactional(
                FlowNodeRegisterMsgService.class,
                "saveFlowNodeRegisterMsg",
                FlowNodeRegisterMsg.class
        );
        assertTransactional(
                CentralPubkeyEmpowerMsgService.class,
                "saveCentralPubkeyEmpowerMsg",
                CentralPubkeyEmpowerMsg.class
        );
        assertTransactional(
                FlowNodeLockedMsgService.class,
                "saveFlowNodeLockedMsg",
                FlowNodeLockedMsg.class
        );
        assertTransactional(
                TransactionRecordMsgService.class,
                "saveTransactionRecordMsg",
                TransactionRecordMsg.class
        );
        assertTransactional(
                TransactionMountMsgService.class,
                "saveTransactionMountMsg",
                TransactionMountMsg.class
        );
        assertTransactional(
                Class.forName("com.cooperativesolutionism.nmsci.service.impl.CentralPubkeyLockedMsgPersistenceService"),
                "save",
                CentralPubkeyLockedMsg.class
        );
    }

    private void assertTransactional(Class<?> serviceClass, String methodName, Class<?> parameterType) throws NoSuchMethodException {
        Method method = serviceClass.getMethod(methodName, parameterType);

        assertNotNull(
                method.getAnnotation(Transactional.class),
                serviceClass.getSimpleName() + "." + methodName + " must persist the business message and msg_abstracts in one transaction"
        );
    }
}
