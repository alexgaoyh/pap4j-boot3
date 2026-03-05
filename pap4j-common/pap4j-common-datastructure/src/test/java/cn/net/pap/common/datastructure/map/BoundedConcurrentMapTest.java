package cn.net.pap.common.datastructure.map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BoundedConcurrentMap 的单元测试
 */
class BoundedConcurrentMapTest {

    private BoundedConcurrentMap<String, Integer> boundedMap;
    private final long MAX_CAPACITY = 3; // 为了方便单线程测试边界，容量设置小一点

    @BeforeEach
    void setUp() {
        // 每个测试用例执行前，初始化一个最大容量为 3 的 Map
        boundedMap = new BoundedConcurrentMap<>(MAX_CAPACITY);
    }

    @Test
    @DisplayName("测试构造函数异常：容量必须大于0")
    void testConstructorException() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            new BoundedConcurrentMap<>(0);
        });
    }

    @Test
    @DisplayName("测试 tryPut：正常放入与容量溢出熔断")
    void testTryPutAndCapacityLimit() {
        // 1. 正常放入 3 个元素
        Assertions.assertTrue(boundedMap.tryPut("K1", 1));
        Assertions.assertTrue(boundedMap.tryPut("K2", 2));
        Assertions.assertTrue(boundedMap.tryPut("K3", 3));
        Assertions.assertEquals(3, boundedMap.mappingCount());

        // 2. 容量已满，尝试放入新元素，应该被拒绝 (返回 false)
        Assertions.assertFalse(boundedMap.tryPut("K4", 4));
        Assertions.assertNull(boundedMap.get("K4")); // 确认没放进去
        Assertions.assertEquals(3, boundedMap.mappingCount()); // 容量依然是 3

        // 3. 容量已满，尝试更新已存在的元素，应该成功 (返回 true)
        Assertions.assertTrue(boundedMap.tryPut("K1", 100));
        Assertions.assertEquals(100, boundedMap.get("K1"));
        Assertions.assertEquals(3, boundedMap.mappingCount());
    }

    @Test
    @DisplayName("测试 compute：原子计算与容量拦截")
    void testCompute() {
        // 1. 正常 compute 放满
        boundedMap.compute("K1", (k, v) -> 1);
        boundedMap.compute("K2", (k, v) -> 2);
        boundedMap.compute("K3", (k, v) -> 3);
        Assertions.assertEquals(3, boundedMap.mappingCount());

        // 2. 容量已满，尝试 compute 新 Key，应该触发熔断返回 null
        Integer result = boundedMap.compute("K4", (k, v) -> 4);
        Assertions.assertNull(result, "容量已满，新增 Key 的 compute 应该返回 null");
        Assertions.assertFalse(boundedMap.containsKey("K4"));

        // 3. 容量已满，尝试 compute 修改老 Key，应该成功
        Integer updateResult = boundedMap.compute("K1", (k, v) -> v + 10);
        Assertions.assertEquals(11, updateResult);
        Assertions.assertEquals(11, boundedMap.get("K1"));
    }

    @Test
    @DisplayName("测试 computeIfAbsent：缺少即放入与容量拦截")
    void testComputeIfAbsent() {
        boundedMap.computeIfAbsent("K1", k -> 1);
        boundedMap.computeIfAbsent("K2", k -> 2);
        boundedMap.computeIfAbsent("K3", k -> 3);

        // 容量已满，再 absent 放入应该被拒，返回 null
        Integer result = boundedMap.computeIfAbsent("K4", k -> 4);
        Assertions.assertNull(result);
        Assertions.assertEquals(3, boundedMap.mappingCount());
    }

    @Test
    @DisplayName("测试 computeIfPresent：存在即计算与安全释放")
    void testComputeIfPresent() {
        boundedMap.tryPut("K1", 1);

        // 修改已存在的 Key
        boundedMap.computeIfPresent("K1", (k, v) -> v + 1);
        Assertions.assertEquals(2, boundedMap.get("K1"));

        // 模拟限流器的安全释放：减到 0 返回 null 删除 Key
        boundedMap.computeIfPresent("K1", (k, v) -> null);
        Assertions.assertFalse(boundedMap.containsKey("K1"));
        Assertions.assertEquals(0, boundedMap.mappingCount());
    }

    @Test
    @DisplayName("测试空值安全：不允许 null 键和值")
    void testNullSafety() {
        Assertions.assertThrows(NullPointerException.class, () -> boundedMap.tryPut(null, 1));
        Assertions.assertThrows(NullPointerException.class, () -> boundedMap.tryPut("K1", null));
    }

    @Test
    @DisplayName("多线程高并发测试：验证软限制(Soft Bound)的防 OOM 能力")
    void testConcurrentSoftBound() throws InterruptedException {
        int maxCap = 100; // 设置阈值为 100
        BoundedConcurrentMap<String, Integer> concurrentMap = new BoundedConcurrentMap<>(maxCap);

        int threadCount = 500; // 模拟 500 个并发线程疯狂塞入不同数据
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.execute(() -> {
                try {
                    // 尝试放入 500 个不同的 Key
                    boolean success = concurrentMap.tryPut("Key-" + index, index);
                    if (success) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(); // 等待所有线程执行完毕
        executorService.shutdown();

        long actualSize = concurrentMap.mappingCount();
        System.out.println("并发塞入 500 个元素，最大容量限制: " + maxCap + "，最终 Map 实际大小: " + actualSize);
        System.out.println("成功写入次数: " + successCount.get());

        // 【核心断言】
        // 由于是高并发下的软限制 (微小的时间差内可能有几个线程同时通过了 if 校验)，
        // 最终的 size 可能会稍微大于 100 (比如 101, 102)，但这对于防 OOM 来说是完全达标的。
        // 我们断言实际大小一定远小于总并发数(500)，且大致在 maxCap 附近。
        Assertions.assertTrue(actualSize >= maxCap && actualSize <= maxCap + 50, "实际大小应该被软限制在接近最大容量的范围内");
    }
}
