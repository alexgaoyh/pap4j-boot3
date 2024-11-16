package cn.net.pap.example.proguard.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleRateLimiter {

    private final ConcurrentHashMap<String, AtomicInteger> accessCounts = new ConcurrentHashMap<>();

    private SimpleRateLimiter() {
    }

    private static final SimpleRateLimiter INSTANCE = new SimpleRateLimiter();

    public static synchronized SimpleRateLimiter getInstance() {
        return INSTANCE;
    }

    public static Integer tryAcquire(String key) {
        return INSTANCE.accessCounts.compute(key, (k, v) -> {
            if (v == null) {
                return new AtomicInteger(1);
            } else {
                v.incrementAndGet();
                return v;
            }
        }).get();
    }

    public static Integer release(String key) {
        AtomicInteger count = INSTANCE.accessCounts.get(key);
        if (count != null) {
            int newValue = count.decrementAndGet();
            if (newValue <= 0) {
                INSTANCE.accessCounts.remove(key);
            }
            return newValue;
        }
        return 0;
    }
}
