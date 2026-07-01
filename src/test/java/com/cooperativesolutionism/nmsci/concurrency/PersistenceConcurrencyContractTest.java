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
                long.class,
                UUID.class
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
        // 性能审计 H1（写事务收窄）：四个消息写服务把 CPU 密集的 PoW/验签/央签移出事务、只把「冲突不变式复检 +
        // msg_abstract 与实体的原子落库」收进窄事务（经 TransactionTemplate），避免 ECDSA 期间持有连接池连接。
        // 原子性契约不变，仅落地机制由整方法 @Transactional 改为 TransactionTemplate（与 CentralPubkeyLockedMsgService 一致）。
        assertPersistsThroughTransactionTemplate(FlowNodeRegisterMsgService.class);
        assertPersistsThroughTransactionTemplate(CentralPubkeyEmpowerMsgService.class);
        assertPersistsThroughTransactionTemplate(FlowNodeLockedMsgService.class);
        assertPersistsThroughTransactionTemplate(TransactionRecordMsgService.class);
        assertPersistsThroughTransactionTemplate(CentralPubkeyLockedMsgService.class);
        // 交易挂载写入仍走专用 @Transactional 写服务（内含 findByIdForUpdate 行锁 + 分配），保持整方法事务。
        assertTransactional(
                TransactionMountWriteService.class,
                "saveAndAllocate",
                TransactionMountMsg.class
        );
    }

    private void assertTransactional(Class<?> serviceClass, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException {
        Method method = serviceClass.getMethod(methodName, parameterTypes);

        assertNotNull(
                method.getAnnotation(Transactional.class),
                serviceClass.getSimpleName() + "." + methodName + " must persist the business message and msg_abstracts in one transaction"
        );
    }

    private void assertPersistsThroughTransactionTemplate(Class<?> serviceClass) throws NoSuchFieldException {
        assertNotNull(
                serviceClass.getDeclaredField("transactionTemplate"),
                serviceClass.getSimpleName() + " must persist the business message and msg_abstracts in one transaction via TransactionTemplate"
        );
    }
}
