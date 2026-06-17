package com.cooperativesolutionism.nmsci.block;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BlockGenerationLockTest {

    @Test
    void usesTransactionScopedPostgresAdvisoryLock() {
        BlockGenerationLock lock = new BlockGenerationLock();
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery("select pg_advisory_xact_lock(:lockKey)")).thenReturn(query);
        when(query.setParameter("lockKey", 0x4E4D534349424C4BL)).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1);
        ReflectionTestUtils.setField(lock, "entityManager", entityManager);

        lock.lock();

        verify(entityManager).createNativeQuery("select pg_advisory_xact_lock(:lockKey)");
        verify(query).setParameter("lockKey", 0x4E4D534349424C4BL);
        verify(query).getSingleResult();
    }
}
