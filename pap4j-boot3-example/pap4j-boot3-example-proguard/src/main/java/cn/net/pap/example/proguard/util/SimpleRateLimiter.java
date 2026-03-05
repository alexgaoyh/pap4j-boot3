package cn.net.pap.example.proguard.util;

import java.util.concurrent.ConcurrentHashMap;

public class SimpleRateLimiter {

    private final ConcurrentHashMap<String, Integer> accessCounts = new ConcurrentHashMap<>();

    private SimpleRateLimiter() {
    }

    private static final SimpleRateLimiter INSTANCE = new SimpleRateLimiter();

    public static SimpleRateLimiter getInstance() {
        return INSTANCE;
    }

    public static boolean tryAcquire(String key, int limit) {
        // 使用单元素数组将 Lambda 内部的结果带出来
        boolean[] acquired = new boolean[]{false};

        // compute 的 Lambda 内部天然是原子操作（由 CHM 桶锁保证）
        INSTANCE.accessCounts.compute(key, (k, currentCount) -> {
            // 如果为空，说明当前并发数为 0
            int count = (currentCount == null) ? 0 : currentCount;

            if (count < limit) {
                // 允许放行：记录成功，并把 Map 里的值 +1
                acquired[0] = true;
                return count + 1;
            } else {
                // 超过限流：记录失败，直接返回原值（不改变 Map 的状态，也不需要回滚！）
                acquired[0] = false;
                return currentCount;
            }
        });

        return acquired[0];
    }

    public static void release(String key) {
        // 安全释放，减到 0 自动清理，防止内存泄露
        INSTANCE.accessCounts.computeIfPresent(key, (k, currentCount) -> {
            int newValue = currentCount - 1;
            return newValue <= 0 ? null : newValue;
        });
    }

}