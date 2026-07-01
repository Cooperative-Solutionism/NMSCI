package com.cooperativesolutionism.nmsci.controller;

import com.cooperativesolutionism.nmsci.verifier.ChainVerificationResult;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * {@code GET /verify/chain} 的结果缓存 + 单航道（single-flight）执行器，用于把"无鉴权全量回放"端点
 * 从 CPU/IO 放大入口收敛为低成本端点：
 * <ul>
 *   <li>链未变化（目录指纹 + stateful 相同）时直接复用上次验证结果，避免重复全量解析/回放；</li>
 *   <li>指纹变化需要重算时，仅允许一次验证在跑，其余并发请求阻塞合并到同一次计算并复用其结果，
 *       杜绝同一时刻多份全量回放并发执行。</li>
 * </ul>
 * 链为 append-only，仅需保留最近一个 (指纹, stateful) 的结果（内存有界）。指纹为 {@code null}（无法计算）时不缓存。
 */
final class ChainVerificationCache {

    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicReference<Entry> latest = new AtomicReference<>();

    ChainVerificationResult get(String fingerprint, boolean stateful, Supplier<ChainVerificationResult> compute) {
        if (fingerprint == null) {
            // 指纹不可用：不缓存，直接计算（计算路径自行处理 IO/解析失败）。
            return compute.get();
        }

        Key key = new Key(fingerprint, stateful);

        // 快路径：命中缓存则无锁返回（最常见：链在出块间隔内不变）。
        Entry cached = latest.get();
        if (cached != null && cached.key().equals(key)) {
            return cached.result();
        }

        // 慢路径：单航道串行化，确保并发请求只触发一次验证。
        lock.lock();
        try {
            cached = latest.get();
            if (cached != null && cached.key().equals(key)) {
                return cached.result();
            }
            ChainVerificationResult result = compute.get();
            latest.set(new Entry(key, result));
            return result;
        } finally {
            lock.unlock();
        }
    }

    private record Key(String fingerprint, boolean stateful) {
    }

    private record Entry(Key key, ChainVerificationResult result) {
    }
}
