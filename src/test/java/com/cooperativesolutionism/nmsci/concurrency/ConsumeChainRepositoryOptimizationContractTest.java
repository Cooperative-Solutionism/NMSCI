package com.cooperativesolutionism.nmsci.concurrency;

import com.cooperativesolutionism.nmsci.repository.ConsumeChainEdgeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumeChainRepositoryOptimizationContractTest {

    @Test
    void repositoryProvidesDatabaseAggregatesForReturningFlowRate() throws NoSuchMethodException {
        Method sourceTargetMethod = ConsumeChainEdgeRepository.class.getMethod(
                "aggregateReturningFlowRate",
                java.util.UUID.class,
                java.util.UUID.class,
                short.class,
                Long.class,
                Long.class
        );
        Method targetMethod = ConsumeChainEdgeRepository.class.getMethod(
                "aggregateReturningFlowRateByTarget",
                java.util.UUID.class,
                short.class,
                Long.class,
                Long.class
        );

        assertAggregateQuery(sourceTargetMethod);
        assertAggregateQuery(targetMethod);
    }

    private static void assertAggregateQuery(Method method) {
        Query query = method.getAnnotation(Query.class);

        assertNotNull(query, method.getName() + " must define an aggregate SQL query");
        assertTrue(query.nativeQuery(), method.getName() + " must keep DISTINCT ON semantics with native PostgreSQL SQL");
        assertTrue(query.value().contains("DISTINCT ON (c.chain)"), method.getName() + " must de-duplicate by chain");
        assertTrue(query.value().toLowerCase().contains("sum("), method.getName() + " must aggregate in the database");
    }
}
