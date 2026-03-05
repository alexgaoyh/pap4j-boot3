package cn.net.pap.example.proguard.util;

import java.util.concurrent.ConcurrentHashMap;

public class SimpleRateLimiter {

    // ConcurrentHashMap 原生不支持设置“最大容量界限”，我们可以通过在代码逻辑中增加一道**“容量熔断机制”**，纯手工把它变成一个有界的 Map。
    // 更进一步的解决方案可以是使用 Caffine 来做记录。
    private final ConcurrentHashMap<String, Integer> accessCounts = new ConcurrentHashMap<>();

    // 增加一个最大容量阈值，防止 OOM (比如最多允许 10000 个不同的 Key 同时存在)
    private static final long MAX_KEYS_CAPACITY = 10000;

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
            if (currentCount != null) {
                // 情况一：Key 已经存在，说明它没触发整体容量限制。直接走正常限流逻辑
                if (currentCount < limit) {
                    acquired[0] = true;
                    return currentCount + 1;
                } else {
                    acquired[0] = false;
                    return currentCount;
                }
            } else {
                // 情况二：Key 不存在，这是一个全新的请求。我们需要评估 Map 的容量！
                // 使用 mappingCount() 获取当前元素个数（比 size() 更适合并发场景）
                if (INSTANCE.accessCounts.mappingCount() >= MAX_KEYS_CAPACITY) {
                    // Map 容量已满！触发全局熔断。记录获取失败，并且【核心】返回 null。在 compute 里返回 null，ConcurrentHashMap 就不会把这个新 Key 塞进去，完美避免 OOM！
                    acquired[0] = false;
                    return null;
                }
                // Map 容量未满，且当前新 Key 并发数为 0
                if (0 < limit) {
                    acquired[0] = true;
                    return 1; // 放入初始值 1
                } else {
                    acquired[0] = false;
                    return null;
                }
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