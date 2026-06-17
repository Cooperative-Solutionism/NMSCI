package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.model.TransactionMountMsg;
import com.cooperativesolutionism.nmsci.model.CentralPubkeyEmpowerMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeLockedMsg;
import com.cooperativesolutionism.nmsci.model.FlowNodeRegisterMsg;
import com.cooperativesolutionism.nmsci.model.TransactionRecordMsg;
import com.cooperativesolutionism.nmsci.repository.ConsumeChainRepository;
import com.cooperativesolutionism.nmsci.repository.TransactionRecordMsgRepository;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyEmpowerMsgService;
import com.cooperativesolutionism.nmsci.service.CentralPubkeyLockedMsgService;
import com.cooperativesolutionism.nmsci.service.FlowNodeLockedMsgService;
import com.cooperativesolutionism.nmsci.service.FlowNodeRegisterMsgService;
import com.cooperativesolutionism.nmsci.service.TransactionMountWriteService;
import com.cooperativesolutionism.nmsci.service.TransactionRecordMsgService;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
    void openConsumeChainsAreLockedForUpdateDuringAllocation() throws NoSuchMethodException {
        Method method = ConsumeChainRepository.class.getMethod(
                "lockOpenChainsForAllocation",
                UUID.class,
                Short.class,
                long.class
        );
        Query query = method.getAnnotation(Query.class);

        assertNotNull(query, "allocation chain selection must be declared as @Query");
        assertTrue(query.nativeQuery(), "allocation chain selection must be a native query");
        assertTrue(
                query.value().toLowerCase().contains("for update"),
                "allocation chain selection must lock selected rows FOR UPDATE"
        );
        assertTrue(
                StringUtils.countOccurrencesOf(query.value().toLowerCase(), "is_loop = false") >= 2,
                "is_loop = false must be applied at BOTH the outer (locking) query and the inner window subquery, "
                        + "so a concurrently-looped row cannot be locked"
        );
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
                TransactionMountWriteService.class,
                "saveAndAllocate",
                TransactionMountMsg.class
        );
        assertNotNull(
                CentralPubkeyLockedMsgService.class.getDeclaredField("transactionTemplate"),
                "CentralPubkeyLockedMsgService must persist the lock message and msg_abstracts through TransactionTemplate"
        );
    }

    private void assertTransactional(Class<?> serviceClass, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = serviceClass.getMethod(methodName, parameterTypes);

        assertNotNull(
                method.getAnnotation(Transactional.class),
                serviceClass.getSimpleName() + "." + methodName + " must persist the business message and msg_abstracts in one transaction"
        );
    }
}
