package cn.net.pap.common.datastructure.lock;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IdempotentTaskLock 的纯 Java 单元测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IdempotentTaskLockTest {

    private static final Logger log = LoggerFactory.getLogger(IdempotentTaskLockTest.class);
    private static ScheduledExecutorService scheduler;

    @BeforeAll
    static void initAll() {
        // 全局初始化一次调度器
        scheduler = Executors.newSingleThreadScheduledExecutor();
        IdempotentTaskLock.init(scheduler);
    }

    @AfterAll
    static void tearDownAll() {
        // 1. 清空全局静态内存，防止污染其他测试类
        IdempotentTaskLock.clear();

        // 2. 销毁调度器，释放后台线程
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @BeforeEach
    void setUp() {
        // 每次测试前清空锁状态，保证测试隔离性
        IdempotentTaskLock.clear();
    }

    @Test
    @Order(1)
    @DisplayName("1. 基础流程测试：加锁 -> 成功释放 -> 冷却拦截 -> 冷却过期")
    void testBasicAcquireAndReleaseSuccess() throws InterruptedException {
        String key = "test_basic_key";

        // 1. 第一次获取锁，应该成功
        assertTrue(IdempotentTaskLock.tryAcquire(key), "首次加锁应该成功");
        assertEquals(1, IdempotentTaskLock.getStatus(key), "状态应该是 RUNNING (1)");

        // 2. 锁未释放时，再次获取，应该被拦截（幂等生效）
        assertFalse(IdempotentTaskLock.tryAcquire(key), "处于 RUNNING 状态时不应再次加锁成功");

        // 3. 业务执行成功，释放锁，设置 1 秒的冷却期
        assertTrue(IdempotentTaskLock.release(key, true, 1), "释放锁应该成功");
        assertEquals(2, IdempotentTaskLock.getStatus(key), "状态应该是 COMPLETED (2)");

        // 4. 处于冷却期内，再次获取锁，应该被拦截
        assertFalse(IdempotentTaskLock.tryAcquire(key), "冷却期内不应加锁成功");

        // 5. 等待冷却期过去 (稍微多等一点时间确保纳米时钟走过)
        TimeUnit.MILLISECONDS.sleep(1100);

        // 6. 冷却期过后，再次获取，应该成功复用并转换为 RUNNING
        assertTrue(IdempotentTaskLock.tryAcquire(key), "冷却期过后应该能再次加锁");
        assertEquals(1, IdempotentTaskLock.getStatus(key), "状态应该再次变为 RUNNING (1)");
    }

    @Test
    @Order(2)
    @DisplayName("2. 业务异常流程测试：加锁 -> 失败释放 -> 立即重试成功")
    void testReleaseFailureAndRetry() {
        String key = "test_fail_key";

        // 1. 获取锁
        assertTrue(IdempotentTaskLock.tryAcquire(key));

        // 2. 模拟业务发生异常，释放锁 (success = false)
        assertTrue(IdempotentTaskLock.release(key, false));

        // 因为我们的设计中，release(false) 会尝试将 PENDING 状态从 Map 中安全移除
        // 所以此时查出来的状态应该是默认的 PENDING (0)
        assertEquals(0, IdempotentTaskLock.getStatus(key), "失败释放后，键应该被移除，查询应返回 PENDING (0)");

        // 3. 业务重试，应该立刻成功，无需等待冷却
        assertTrue(IdempotentTaskLock.tryAcquire(key), "业务失败释放后，重试应该立刻成功");
    }

    @Test
    @Order(3)
    @DisplayName("3. 高并发抢锁测试：100 个线程同时抢一把锁，只能有 1 个成功")
    void testHighConcurrencyAcquire() throws InterruptedException {
        String key = "test_concurrent_key";
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 所有线程在此等待，确保同时起跑
                    if (IdempotentTaskLock.tryAcquire(key)) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }

        startLatch.countDown(); // 100 个线程同时触发
        endLatch.await();       // 等待所有线程执行完毕

        // 验证并发安全性
        assertEquals(1, successCount.get(), "在高并发下，必须且只能有一个线程抢到锁");
    }

    @Test
    @Order(4)
    @DisplayName("4. 强制重置接口 (safeForceReset) 测试")
    void testSafeForceReset() {
        String key = "test_reset_key";

        IdempotentTaskLock.tryAcquire(key);
        // RUNNING 状态下，不允许重置
        assertFalse(IdempotentTaskLock.safeForceReset(key), "RUNNING 状态不应被强制重置");

        IdempotentTaskLock.release(key, true, 10);
        // COMPLETED (冷却中) 状态下，不允许重置，保护防重契约
        assertFalse(IdempotentTaskLock.safeForceReset(key), "COMPLETED 状态不应被强制重置");
    }

    @Test
    @Order(5)
    @DisplayName("5. 全局后台扫表清理机制 (Sweeper) 测试")
    void testSweepExpiredKeys() throws Exception {
        String key = "test_sweeper_key";

        // 获取并成功释放，设置极其短暂的冷却时间 (100毫秒)
        IdempotentTaskLock.tryAcquire(key);
        IdempotentTaskLock.release(key, true); // 使用默认 60s

        // 为了便于测试，我们通过反射调用 status 的 release，手动塞入 10 毫秒的过期时间
        IdempotentTaskLock.tryAcquire(key + "_2");
        IdempotentTaskLock.release(key + "_2", true, 0); // 0秒冷却，立刻过期

        // 等待一点时间确保 key_2 过期
        TimeUnit.MILLISECONDS.sleep(50);

        // 使用反射强制调用私有的 sweepExpiredKeys 方法，而不是死等 30 秒定时任务
        Method sweepMethod = IdempotentTaskLock.class.getDeclaredMethod("sweepExpiredKeys");
        sweepMethod.setAccessible(true);
        sweepMethod.invoke(null);

        // 验证结果：
        // key (冷却中，未过期) 应该还在
        assertEquals(2, IdempotentTaskLock.getStatus(key), "未过期的锁应该保留在内存中");

        // key_2 (已过期) 应该被清理掉 (返回状态 0)
        assertEquals(0, IdempotentTaskLock.getStatus(key + "_2"), "已过期的锁应该被后台扫表清除");
    }

}