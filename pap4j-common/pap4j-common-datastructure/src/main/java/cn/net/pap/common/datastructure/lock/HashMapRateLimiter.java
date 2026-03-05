package cn.net.pap.common.datastructure.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 改进版：同样基于每 key 一个 Status，
 * - release 返回 boolean 表示是否成功释放
 * - 可选 成功完成时自动尝试移除 map 中的已完成条目（避免内存泄漏）
 * - safeForceReset 只有在非 RUNNING 时才移除，避免并发导致同时两个 RUNNING
 *
 *  @deprecated 请使用 {@link cn.net.pap.common.datastructure.lock.IdempotentTaskLock} 替代
 */
@Deprecated
public class HashMapRateLimiter {

    private enum State {
        PENDING(0),      // 待执行
        RUNNING(1),      // 执行中
        COMPLETED(2);    // 已完成

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private static class Status {
        final AtomicReference<State> state;

        Status(State initialState) {
            this.state = new AtomicReference<>(initialState);
        }

        boolean tryAcquire() {
            return state.compareAndSet(State.PENDING, State.RUNNING);
        }

        /**
         * 尝试释放运行锁：
         *
         * @param success 如果 true -> RUNNING -> COMPLETED
         *                如果 false -> RUNNING -> PENDING（允许重试）
         * @return true 如果成功做了状态变更；false 表示当前不是 RUNNING（可能被其它操作修改）
         */
        boolean release(boolean success) {
            return state.compareAndSet(State.RUNNING, success ? State.COMPLETED : State.PENDING);
        }

        State getState() {
            return state.get();
        }
    }

    private final ConcurrentHashMap<String, Status> accessCounts = new ConcurrentHashMap<>();

    private HashMapRateLimiter() {
    }

    private static final HashMapRateLimiter INSTANCE = new HashMapRateLimiter();

    public static boolean tryAcquire(String key) {
        // 使用 compute 保证同一 key 的插入/检查是原子的
        final boolean[] acquired = {false};
        INSTANCE.accessCounts.compute(key, (k, existingStatus) -> {
            if (existingStatus == null) {
                acquired[0] = true;
                return new Status(State.RUNNING); // 直接创建 RUNNING，表示持有
            }
            if (existingStatus.tryAcquire()) {
                acquired[0] = true;
            }
            return existingStatus;
        });
        return acquired[0];
    }

    /**
     * 释放运行标志
     *
     * @param key     key
     * @param success true 表示执行成功 -> 转 COMPLETED（并尝试清理映射）
     * @return true 若成功做了状态变更（RUNNING->COMPLETED 或 RUNNING->PENDING），false 表示释放失败（不在 RUNNING）
     */
    public static boolean release(String key, boolean success) {
        Status status = INSTANCE.accessCounts.get(key);
        if (status == null) {
            return false;
        }
        boolean changed = status.release(success);
        if (changed && success) {
            // 如果变更为 COMPLETED，尝试移除条目（仅当映射仍然指向这个 Status 实例时）
//            if (status.getState() == State.COMPLETED) {
//                INSTANCE.accessCounts.remove(key, status);
//            }
        }
        return changed;
    }

    /**
     * 安全的强制重置：只有在当前不是 RUNNING 时才移除并返回 true；若正在 RUNNING 则返回 false（避免并发执行）。
     */
    public static boolean safeForceReset(String key) {
        Status status = INSTANCE.accessCounts.get(key);
        if (status == null) {
            return false;
        }
        if (status.getState() == State.RUNNING) {
            return false;
        }
        // 仅在映射仍然指向该 status 时移除
        return INSTANCE.accessCounts.remove(key, status);
    }

    /**
     * 非安全的强制移除（调试用）：直接移除，不考虑 RUNNING。慎用。
     */
    @Deprecated
    public static boolean forceResetUnsafe(String key) {
        return INSTANCE.accessCounts.remove(key) != null;
    }

    public static int getStatus(String key) {
        Status status = INSTANCE.accessCounts.get(key);
        return status == null ? State.PENDING.getCode() : status.getState().getCode();
    }
}
