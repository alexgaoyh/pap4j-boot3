package cn.net.pap.common.datastructure.lock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * <p><strong>HashMapRateLimiter</strong> 是一个基于 {@link ConcurrentHashMap} 的基础限流器。</p>
 *
 * <p>它提供了一种改进的机制，为每个键使用一个状态对象。
 * 特性包括：</p>
 * <ul>
 *     <li>release 方法返回一个布尔值以指示是否成功。</li>
 *     <li>可选自动移除已完成的项，以防止内存泄漏。</li>
 *     <li>安全的强制重置，仅在项不处于 RUNNING 状态时才将其移除。</li>
 * </ul>
 *
 * @deprecated 请改用 {@link cn.net.pap.common.datastructure.lock.IdempotentTaskLock}。
 */
@Deprecated
public class HashMapRateLimiter {

    /**
     * <p>表示与特定键关联的锁的状态。</p>
     */
    private enum State {
        /** <p>指示任务正在等待执行。</p> */
        PENDING(0),
        
        /** <p>指示任务当前正在执行。</p> */
        RUNNING(1),
        
        /** <p>指示任务已完成执行。</p> */
        COMPLETED(2);

        private final int code;

        State(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /**
     * <p>维护一个键的原子状态的内部类。</p>
     */
    private static class Status {
        final AtomicReference<State> state;

        Status(State initialState) {
            this.state = new AtomicReference<>(initialState);
        }

        /**
         * <p>尝试将状态从 PENDING 转换为 RUNNING。</p>
         *
         * @return <strong>true</strong> 如果成功，否则返回 <strong>false</strong>。
         */
        boolean tryAcquire() {
            return state.compareAndSet(State.PENDING, State.RUNNING);
        }

        /**
         * <p>尝试释放锁并转换其状态。</p>
         *
         * <ul>
         *     <li>如果 success 为 true，它将从 RUNNING 转换为 COMPLETED。</li>
         *     <li>如果 success 为 false，它将从 RUNNING 转换回 PENDING。</li>
         * </ul>
         *
         * @param success 执行结果。
         * @return <strong>true</strong> 如果状态成功更改，否则返回 <strong>false</strong>。
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

    /**
     * <p>尝试获取给定键的锁。</p>
     *
     * @param key 要获取锁的键。
     * @return <strong>true</strong> 如果锁已获取，否则返回 <strong>false</strong>。
     */
    public static boolean tryAcquire(String key) {
        // 使用 compute 保证同一 key 的插入/检查是原子的
        final boolean[] acquired = {false};
        INSTANCE.accessCounts.compute(key, (k, existingStatus) -> {
            if (existingStatus == null) {
                acquired[0] = true;
                return new Status(State.RUNNING);
            }
            if (existingStatus.tryAcquire()) {
                acquired[0] = true;
            }
            return existingStatus;
        });
        return acquired[0];
    }

    /**
     * <p>释放指定键的锁。</p>
     *
     * @param key     与锁关联的键。
     * @param success 指示任务是否成功。
     * @return <strong>true</strong> 如果释放触发了状态更改，否则返回 <strong>false</strong>。
     */
    public static boolean release(String key, boolean success) {
        Status status = INSTANCE.accessCounts.get(key);
        if (status == null) {
            return false;
        }
        boolean changed = status.release(success);
        if (changed && success) {
            // 这里可以放置可选的移除逻辑。
        }
        return changed;
    }

    /**
     * <p>安全地强制重置给定键（通过将其移除），前提是它当前未处于 RUNNING 状态。</p>
     *
     * @param key 要重置的键。
     * @return <strong>true</strong> 如果重置成功，如果锁当前处于 RUNNING 状态或丢失则返回 <strong>false</strong>。
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
     * <p>不安全地强制移除一个键，且不检查其当前状态。</p>
     *
     * @param key 要移除的键。
     * @return <strong>true</strong> 如果已被移除，否则返回 <strong>false</strong>。
     * @deprecated 请谨慎使用，主要用于调试目的。
     */
    @Deprecated
    public static boolean forceResetUnsafe(String key) {
        return INSTANCE.accessCounts.remove(key) != null;
    }

    /**
     * <p>检索表示一个键的当前状态的整数代码。</p>
     *
     * @param key 键。
     * @return 状态代码。如果丢失则返回 PENDING 的代码。
     */
    public static int getStatus(String key) {
        Status status = INSTANCE.accessCounts.get(key);
        return status == null ? State.PENDING.getCode() : status.getState().getCode();
    }
}
