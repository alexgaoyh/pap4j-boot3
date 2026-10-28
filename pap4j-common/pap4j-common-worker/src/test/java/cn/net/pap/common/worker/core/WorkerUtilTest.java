package cn.net.pap.common.worker.core;

import cn.net.pap.common.core.util.NamedThreadFactory;
import cn.net.pap.common.core.util.ThreadPoolUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerUtilTest {

    private static final Logger log = LoggerFactory.getLogger(WorkerUtilTest.class);

    @Test
    @DisplayName("测试基础任务提交、执行与返回值直接获取")
    void testNormalSubmitAndExecute() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(2, 10)) {
            // 提交一个具有明确返回值的业务任务
            TaskSpec<String> spec = new TaskSpec<>("T-NORMAL-1", () -> "处理结果: Hello Worker!");

            TaskSubmission<String> submission = worker.submit(spec);
            assertTrue(submission.accepted());
            assertFalse(submission.duplicate());

            // 【直观取值】：直接从 Future 句柄获取任务执行的真实返回值
            String result = submission.future().get(2, TimeUnit.SECONDS);
            assertEquals("处理结果: Hello Worker!", result);

            // 同时验证订单板上也同步记录了该结果与终态
            TaskRecord record = worker.getRecord("T-NORMAL-1");
            assertNotNull(record);
            assertEquals("处理结果: Hello Worker!", record.result());
            assertEquals(TaskState.SUCCEEDED, record.state());
            assertEquals(1, record.attempt());
        }
    }

    @Test
    @DisplayName("测试并发幂等去重（红线2：不会两个厨师同时做一单）")
    void testIdempotentDeduplication() throws Exception {
        int threads = 20;
        try (WorkerUtil worker = new WorkerUtil(4, 50)) {
            AtomicInteger executionCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(threads);
            List<TaskSubmission<String>> submissions = Collections.synchronizedList(new ArrayList<>());

            ThreadPoolExecutor submitPool = new ThreadPoolExecutor(
                    threads,
                    threads,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(),
                    new NamedThreadFactory("test-submit-", 1)
            );
            try {
                for (int i = 0; i < threads; i++) {
                    submitPool.execute(() -> {
                        try {
                            TaskSpec<String> spec = new TaskSpec<>("DUP-ORDER-1", () -> {
                                executionCount.incrementAndGet();
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    log.error("模拟业务中断", e);
                                    Thread.currentThread().interrupt();
                                }
                                return "ORDER-SUCCESS";
                            });
                            submissions.add(worker.submit(spec));
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertTrue(latch.await(5, TimeUnit.SECONDS));
            } finally {
                ThreadPoolUtil.shutdownAndAwaitTermination(submitPool);
            }

            long acceptedCount = submissions.stream().filter(TaskSubmission::accepted).count();
            long duplicateCount = submissions.stream().filter(TaskSubmission::duplicate).count();

            assertEquals(1, acceptedCount, "只能有一个提交被判定为 ACCEPTED");
            assertEquals(threads - 1, duplicateCount, "其余提交必须被判定为 DUPLICATE");

            // 所有重复提交拿到的都是同一个 Future，均可顺利取到返回值
            for (TaskSubmission<String> sub : submissions) {
                String val = sub.future().get(3, TimeUnit.SECONDS);
                assertEquals("ORDER-SUCCESS", val);
            }

            assertEquals(1, executionCount.get(), "底层 Action 保证绝对只执行 1 次");
            TaskRecord record = worker.getRecord("DUP-ORDER-1");
            assertEquals("ORDER-SUCCESS", record.result());
            assertEquals(TaskState.SUCCEEDED, record.state());
        }
    }

    @Test
    @DisplayName("测试队列满时快速失败与原子回滚（红线3：无永久死锁单）")
    void testQueueSaturationAndRollback() throws Exception {
        // 1 个 Worker，队列容量仅 1
        try (WorkerUtil worker = new WorkerUtil(1, 1)) {
            CountDownLatch blockWorkerLatch = new CountDownLatch(1);
            CountDownLatch workerStartedLatch = new CountDownLatch(1);

            // 任务 1：占用唯一的 Worker 线程
            TaskSpec<Void> task1 = TaskSpec.of("T-BLOCK", () -> {
                workerStartedLatch.countDown();
                try {
                    blockWorkerLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    log.error("阻塞中断", e);
                    Thread.currentThread().interrupt();
                }
            });
            worker.submit(task1);
            assertTrue(workerStartedLatch.await(2, TimeUnit.SECONDS));

            // 任务 2：填满队列容量 1
            TaskSpec<Void> task2 = TaskSpec.of("T-QUEUED", () -> {});
            TaskSubmission<Void> sub2 = worker.submit(task2);
            assertTrue(sub2.accepted());

            // 任务 3：队列已满，必须快速拒绝并回滚
            TaskSpec<Void> task3 = TaskSpec.of("T-REJECT", () -> {});
            TaskSubmission<Void> sub3 = worker.submit(task3);
            assertFalse(sub3.accepted());
            assertFalse(sub3.duplicate());
            assertEquals("排队区已满", sub3.reason());

            // 验证 TaskRecord 状态为 FAILED，且 Future 异常完成，不留脏单
            TaskRecord record3 = worker.getRecord("T-REJECT");
            assertNotNull(record3);
            assertEquals(TaskState.FAILED, record3.state());

            ExecutionException ex = assertThrows(ExecutionException.class, () -> sub3.future().get(1, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof RejectedExecutionException);

            // 释放任务 1
            blockWorkerLatch.countDown();
        }
    }

    @Test
    @DisplayName("测试失败重试全生命周期（进入 WAITING_RETRY -> 闹钟到点回投 -> 成功 SUCCEEDED 并取值）")
    void testRetryLifecycle() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(2, 20)) {
            AtomicInteger attempts = new AtomicInteger(0);

            // 失败 2 次，第 3 次成功；初始延迟 50ms
            TaskSpec<String> spec = new TaskSpec<>("RETRY-TASK-1", () -> {
                int count = attempts.incrementAndGet();
                if (count < 3) {
                    throw new RuntimeException("模拟下游网络抖动 (第" + count + "次)");
                }
                return "RETRY-SUCCESS-VALUE";
            }, 3, 50L);

            TaskSubmission<String> submission = worker.submit(spec);
            assertTrue(submission.accepted());

            // 轮询验证是否曾经进入过 WAITING_RETRY 状态
            boolean enteredWaitingRetry = false;
            long waitDeadline = System.currentTimeMillis() + 1000L;
            while (System.currentTimeMillis() < waitDeadline) {
                TaskRecord r = worker.getRecord("RETRY-TASK-1");
                if (r != null && r.state() == TaskState.WAITING_RETRY) {
                    enteredWaitingRetry = true;
                    assertTrue(r.nextRetryAt() > 0, "等待重试状态必须包含下次重试时间戳");
                    break;
                }
                Thread.sleep(10);
            }
            assertTrue(enteredWaitingRetry, "失败后必须明确跃迁至 WAITING_RETRY 状态并进入闹钟架");

            // 等待最终成功取值
            String result = submission.future().get(3, TimeUnit.SECONDS);
            assertEquals("RETRY-SUCCESS-VALUE", result);
            assertEquals(3, attempts.get());

            TaskRecord finalRecord = worker.getRecord("RETRY-TASK-1");
            assertEquals("RETRY-SUCCESS-VALUE", finalRecord.result());
            assertEquals(TaskState.SUCCEEDED, finalRecord.state());
            assertEquals(3, finalRecord.attempt());
        }
    }

    @Test
    @DisplayName("测试超过最大重试次数转入彻底失败 FAILED")
    void testMaxRetryExceeded() {
        try (WorkerUtil worker = new WorkerUtil(2, 20)) {
            AtomicInteger attempts = new AtomicInteger(0);

            // 最多重试 2 次，必定失败；退避 30ms
            TaskSpec<String> spec = new TaskSpec<>("RETRY-FAIL-1", () -> {
                attempts.incrementAndGet();
                throw new IllegalStateException("始终失败异常");
            }, 2, 30L);

            TaskSubmission<String> submission = worker.submit(spec);
            assertTrue(submission.accepted());

            ExecutionException ex = assertThrows(ExecutionException.class, () -> submission.future().get(3, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof IllegalStateException);
            assertEquals(2, attempts.get());

            TaskRecord record = worker.getRecord("RETRY-FAIL-1");
            assertEquals(TaskState.FAILED, record.state());
            assertEquals(2, record.attempt());
        }
    }

    @Test
    @DisplayName("测试厨师异常免疫（红线10：异常不能让厨师倒下）")
    void testWorkerResilienceOnCrash() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(1, 10)) {
            // 提交一个抛出严重未检查异常的任务（只执行 1 次，不重试，便于断言快速失败）
            TaskSpec<Void> poisonTask = new TaskSpec<>("POISON-1", () -> {
                throw new RuntimeException("致命毒任务");
            }, 1, 10L);
            TaskSubmission<Void> poisonSub = worker.submit(poisonTask);

            assertThrows(ExecutionException.class, () -> poisonSub.future().get(2, TimeUnit.SECONDS));
            assertEquals(TaskState.FAILED, worker.getRecord("POISON-1").state());

            // 再次提交正常任务，验证厨师线程依然健康可用并能正确返回值
            TaskSpec<String> healthyTask = new TaskSpec<>("HEALTHY-1", () -> "HEALTHY-RESULT");
            TaskSubmission<String> healthySub = worker.submit(healthyTask);

            String res = healthySub.future().get(2, TimeUnit.SECONDS);
            assertEquals("HEALTHY-RESULT", res, "厨师线程未受毒任务影响，后续任务正常处理并正确返回值");
        }
    }

    @Test
    @DisplayName("测试优雅打烊排空（红线7：打烊后排空已接单，拒绝新单）")
    void testDrainAndShutdown() throws Exception {
        WorkerUtil worker = new WorkerUtil(2, 20);
        int totalTasks = 10;
        AtomicInteger processed = new AtomicInteger(0);

        for (int i = 0; i < totalTasks; i++) {
            TaskSpec<Integer> spec = new TaskSpec<>("DRAIN-" + i, () -> {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    log.error("排空模拟中断", e);
                    Thread.currentThread().interrupt();
                }
                return processed.incrementAndGet();
            });
            worker.submit(spec);
        }

        // 打烊并排空
        List<TaskSpec<?>> unexecuted = worker.drainAndShutdown(5, TimeUnit.SECONDS);
        assertEquals(0, unexecuted.size(), "规定时间内所有排队中的任务都应排空执行完成");
        assertEquals(totalTasks, processed.get(), "已收单据全部完成处理");

        // 打烊后再提交，直接拒绝
        TaskSpec<Void> afterClose = TaskSpec.of("AFTER-CLOSE", () -> {});
        TaskSubmission<Void> rejectedSub = worker.submit(afterClose);
        assertFalse(rejectedSub.accepted());
        assertEquals("车间已打烊", rejectedSub.reason());
    }

}
