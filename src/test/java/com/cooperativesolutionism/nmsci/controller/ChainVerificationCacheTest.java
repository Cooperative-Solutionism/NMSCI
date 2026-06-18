package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.verifier.ChainVerificationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 覆盖 {@link ChainVerificationCache} 的「结果缓存 + 单航道」语义（#7）：相同 (指纹,stateful) 复用、
 * 指纹/标志变化重算、null 指纹不缓存、并发同键只触发一次计算。
 */
class ChainVerificationCacheTest {

    @Test
    void reusesResultForSameFingerprintAndStateful() {
        ChainVerificationCache cache = new ChainVerificationCache();
        AtomicInteger computeCount = new AtomicInteger();
        Supplier<ChainVerificationResult> compute = countingSupplier(computeCount);

        ChainVerificationResult first = cache.get("fp-1", true, compute);
        ChainVerificationResult second = cache.get("fp-1", true, compute);

        assertEquals(1, computeCount.get(), "链未变化时不应重算");
        assertSame(first, second, "应复用缓存的同一结果实例");
    }

    @Test
    void recomputesWhenFingerprintChanges() {
        ChainVerificationCache cache = new ChainVerificationCache();
        AtomicInteger computeCount = new AtomicInteger();
        Supplier<ChainVerificationResult> compute = countingSupplier(computeCount);

        cache.get("fp-1", true, compute);
        cache.get("fp-2", true, compute);

        assertEquals(2, computeCount.get(), "指纹变化（链增长）应重算");
    }

    @Test
    void recomputesWhenStatefulFlagChanges() {
        ChainVerificationCache cache = new ChainVerificationCache();
        AtomicInteger computeCount = new AtomicInteger();
        Supplier<ChainVerificationResult> compute = countingSupplier(computeCount);

        cache.get("fp-1", true, compute);
        cache.get("fp-1", false, compute);

        assertEquals(2, computeCount.get(), "stateful 不同对应不同结果，应分别计算");
    }

    @Test
    void neverCachesWhenFingerprintIsNull() {
        ChainVerificationCache cache = new ChainVerificationCache();
        AtomicInteger computeCount = new AtomicInteger();
        Supplier<ChainVerificationResult> compute = countingSupplier(computeCount);

        cache.get(null, true, compute);
        cache.get(null, true, compute);

        assertEquals(2, computeCount.get(), "指纹不可用时不缓存，每次都计算");
    }

    @Test
    void coalescesConcurrentRequestsForSameKeyIntoOneComputation() throws InterruptedException {
        ChainVerificationCache cache = new ChainVerificationCache();
        AtomicInteger computeCount = new AtomicInteger();
        int threadCount = 8;
        CyclicBarrier startTogether = new CyclicBarrier(threadCount);
        Supplier<ChainVerificationResult> slowCompute = () -> {
            computeCount.incrementAndGet();
            sleepQuietly(50);
            return ChainVerificationResult.parseFailure("computed-once");
        };

        List<ChainVerificationResult> results = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                awaitQuietly(startTogether);
                results.add(cache.get("fp-1", true, slowCompute));
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }

        assertEquals(1, computeCount.get(), "并发同键请求应合并为一次计算（单航道）");
        assertEquals(threadCount, results.size());
        ChainVerificationResult shared = results.get(0);
        assertTrue(results.stream().allMatch(r -> r == shared), "所有并发请求应复用同一结果实例");
    }

    private static Supplier<ChainVerificationResult> countingSupplier(AtomicInteger computeCount) {
        return () -> ChainVerificationResult.parseFailure("compute-" + computeCount.incrementAndGet());
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitQuietly(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
