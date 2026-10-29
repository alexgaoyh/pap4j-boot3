package cn.net.pap.common.worker.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkerUtil} 单元测试：提交三态、在途去重、双层容量、状态机、异常快照、句柄释放、优雅关闭。
 */
class WorkerUtilTest {

    private static final Logger log = LoggerFactory.getLogger(WorkerUtilTest.class);

    @Test
    @DisplayName("提交并执行成功：返回值、终态记录均可观察")
    void testSubmitAndExecuteSuccess() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(2, 10)) {
            TaskSubmission<String> submission = worker.submit(TaskSpec.of("T-OK-1", () -> "处理结果"));

            assertTrue(submission.accepted());
            assertFalse(submission.duplicate());
            assertEquals("ACCEPTED", submission.reason());
            assertEquals("处理结果", submission.future().get(2, TimeUnit.SECONDS));

            TaskRecord record = worker.getRecord("T-OK-1");
            assertNotNull(record);
            assertEquals(TaskState.SUCCEEDED, record.state());
            assertEquals("处理结果", record.result());
            assertNull(record.error());
            assertTrue(record.completedAt() > 0L);
            assertEquals(0, worker.getPendingFutureCount(), "终态后句柄必须立即释放");
        }
    }

    @Test
    @DisplayName("失败即终态：异常被快照化，句柄以异常完成")
    void testFailureIsTerminalImmediately() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(2, 10)) {
            AtomicInteger executions = new AtomicInteger(0);
            TaskSubmission<String> submission = worker.submit(TaskSpec.<String>of("T-FAIL-1", () -> {
                executions.incrementAndGet();
                throw new IllegalStateException("boom-detail", new RuntimeException("root-cause"));
            }));

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> submission.future().get(3, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof IllegalStateException);

            TaskRecord record = worker.getRecord("T-FAIL-1");
            assertEquals(TaskState.FAILED, record.state());
            assertNull(record.result());
            assertNotNull(record.error());
            assertEquals(IllegalStateException.class.getName(), record.error().type());
            assertEquals("boom-detail", record.error().message());
            assertTrue(record.error().stackTrace().contains("Caused by: java.lang.RuntimeException: root-cause"),
                    "cause 链应保留在快照中: " + record.error().stackTrace());

            Thread.sleep(100L);
            assertEquals(1, executions.get(), "业务逻辑只执行一次");
            assertEquals(0, worker.getPendingFutureCount());
        }
    }

    @Test
    @DisplayName("在途去重：同一 id 并发提交只执行一次，其余拿到同一个句柄")
    void testIdempotentDeduplication() throws Exception {
        int threads = 20;
        try (WorkerUtil worker = new WorkerUtil(4, 50)) {
            AtomicInteger executions = new AtomicInteger(0);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            List<TaskSubmission<String>> submissions = Collections.synchronizedList(new ArrayList<>());

            for (int i = 0; i < threads; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        submissions.add(worker.submit(TaskSpec.of("DUP-1", () -> {
                            executions.incrementAndGet();
                            Thread.sleep(100L);
                            return "ORDER-SUCCESS";
                        })));
                    } catch (InterruptedException e) {
                        log.error("并发提交被中断", e);
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
                thread.start();
            }

            start.countDown();
            assertTrue(done.await(5, TimeUnit.SECONDS));
            assertEquals(threads, submissions.size());

            assertEquals(1, submissions.stream().filter(TaskSubmission::accepted).count(),
                    "同一 id 只能有一个提交被判定为 ACCEPTED");
            assertEquals(threads - 1, submissions.stream().filter(TaskSubmission::duplicate).count());
            for (TaskSubmission<String> submission : submissions) {
                assertEquals("ORDER-SUCCESS", submission.future().get(3, TimeUnit.SECONDS));
            }
            assertEquals(1, executions.get(), "底层业务逻辑必须只执行一次");
        }
    }

    @Test
    @DisplayName("排队区已满：提交快速失败，记录回滚为 FAILED，不留脏单")
    void testQueueSaturationRollsBack() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(1, 1)) {
            CountDownLatch running = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            // 唯一厨师被占住
            assertTrue(worker.submit(TaskSpec.of("T-BLOCK", () -> {
                running.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            })).accepted());
            assertTrue(running.await(2, TimeUnit.SECONDS));

            // 填满排队区（容量 1）
            assertTrue(worker.submit(TaskSpec.of("T-QUEUED", () -> "Q")).accepted());

            // 再提交必然溢出
            TaskSubmission<String> rejected = worker.submit(TaskSpec.of("T-OVER", () -> "O"));
            assertFalse(rejected.accepted());
            assertFalse(rejected.duplicate());
            assertEquals("排队区已满", rejected.reason());

            TaskRecord record = worker.getRecord("T-OVER");
            assertNotNull(record, "拒收必须在订单板上留下明确终态，避免脏单");
            assertEquals(TaskState.FAILED, record.state());

            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> rejected.future().get(1, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof TaskNotStartedException, "排队区满 = 从未执行，必须带“可安全重投”标记");
            assertEquals(TaskNotStartedException.class.getName(), record.error().type(),
                    "对账靠这一列判定能否无条件重投");

            release.countDown();
        }
    }

    @Test
    @DisplayName("厨师异常免疫：毒任务失败不影响后续任务")
    void testWorkerResilienceOnPoisonTask() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(1, 10)) {
            TaskSubmission<String> poison = worker.submit(TaskSpec.<String>of("T-POISON", () -> {
                throw new RuntimeException("致命毒任务");
            }));
            assertThrows(ExecutionException.class, () -> poison.future().get(2, TimeUnit.SECONDS));
            assertEquals(TaskState.FAILED, worker.getRecord("T-POISON").state());

            TaskSubmission<String> healthy = worker.submit(TaskSpec.of("T-HEALTHY", () -> "HEALTHY"));
            assertEquals("HEALTHY", healthy.future().get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    @DisplayName("异常快照化失败时句柄仍然完成：非取消的 future 绝不能悬挂")
    void testFutureNeverStrandedWhenSnapshotFails() {
        try (WorkerUtil worker = new WorkerUtil(1, 10)) {
            TaskSubmission<String> submission = worker.submit(TaskSpec.<String>of("T-BROKEN-ERROR", () -> {
                // 消息不可读 → 落终态时的快照渲染必然失败（TaskError.of 内部会取 message）
                throw new RuntimeException("主消息") {
                    @Override
                    public String getMessage() {
                        throw new IllegalStateException("异常消息本身不可读，快照化必然失败");
                    }
                };
            }));

            // 若终态路径抛错后直接逃逸，句柄将永远不完成，这里只能等到 TimeoutException
            assertThrows(ExecutionException.class, () -> submission.future().get(2, TimeUnit.SECONDS));
            assertTrue(submission.future().isDone());
        }
    }

    @Test
    @DisplayName("并发占座：重复提交绝不返回 null 句柄")
    void testDuplicateNeverReturnsNullFuture() throws Exception {
        int iterations = 50;
        int threads = 8;
        try (WorkerUtil worker = new WorkerUtil(2, 50)) {
            for (int iter = 0; iter < iterations; iter++) {
                String taskId = "RACE-" + iter;
                CountDownLatch start = new CountDownLatch(1);
                CountDownLatch done = new CountDownLatch(threads);
                CountDownLatch hold = new CountDownLatch(1);
                List<TaskSubmission<String>> submissions = Collections.synchronizedList(new ArrayList<>());

                for (int i = 0; i < threads; i++) {
                    Thread thread = new Thread(() -> {
                        try {
                            start.await();
                            submissions.add(worker.submit(TaskSpec.of(taskId, () -> {
                                hold.await(3, TimeUnit.SECONDS);
                                return "RACE-RESULT";
                            })));
                        } catch (InterruptedException e) {
                            log.error("并发提交被中断", e);
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
                    thread.start();
                }

                start.countDown();
                assertTrue(done.await(5, TimeUnit.SECONDS));
                assertEquals(threads, submissions.size());

                for (TaskSubmission<String> submission : submissions) {
                    assertNotNull(submission.future(), "DUPLICATE 分支绝不能返回 null 句柄");
                }
                assertEquals(1, submissions.stream().filter(TaskSubmission::accepted).count());
                assertEquals(threads - 1, submissions.stream().filter(TaskSubmission::duplicate).count());

                hold.countDown();
                for (TaskSubmission<String> submission : submissions) {
                    assertEquals("RACE-RESULT", submission.future().get(3, TimeUnit.SECONDS));
                }
            }
        }
    }

    @Test
    @DisplayName("终态后再提交同一 id：视为新任务，返回新句柄并重新执行")
    void testResubmitAfterTerminalStartsNewRound() throws Exception {
        try (WorkerUtil worker = new WorkerUtil(2, 10)) {
            AtomicInteger executions = new AtomicInteger(0);
            TaskSubmission<String> first = worker.submit(TaskSpec.of("T-RESUBMIT", () -> {
                if (executions.incrementAndGet() == 1) {
                    throw new IllegalStateException("第一次失败");
                }
                return "SECOND-ROUND";
            }));

            assertThrows(ExecutionException.class, () -> first.future().get(3, TimeUnit.SECONDS));
            assertEquals(TaskState.FAILED, worker.getRecord("T-RESUBMIT").state());

            // 上一轮已终态 → 新一轮：新句柄、真的再执行
            TaskSubmission<String> second = worker.submit(TaskSpec.of("T-RESUBMIT", () -> {
                executions.incrementAndGet();
                return "SECOND-ROUND";
            }));
            assertTrue(second.accepted(), "上一轮已终态，重复提交应视为新任务");
            assertNotSame(first.future(), second.future(), "新一轮必须返回新的句柄");
            assertEquals("SECOND-ROUND", second.future().get(3, TimeUnit.SECONDS));
            assertEquals(TaskState.SUCCEEDED, worker.getRecord("T-RESUBMIT").state());
            assertEquals(2, executions.get());
        }
    }

    @Test
    @DisplayName("订单板满时删除已终结记录腾位置，未终结记录绝不删除")
    void testRegistryRecyclesFinishedRecordsWhenFull() throws Exception {
        // 订单板容量 3：1 张在岗 + 2 张已终结刚好占满
        try (WorkerUtil worker = new WorkerUtil(2, 10, 3)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            TaskSubmission<Void> hold = worker.submit(TaskSpec.of("T-HOLD", () -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            }));
            assertTrue(hold.accepted());
            assertTrue(started.await(3, TimeUnit.SECONDS));

            assertEquals("A", worker.submit(TaskSpec.of("T-FILL-1", () -> "A")).future().get(3, TimeUnit.SECONDS));
            assertEquals("B", worker.submit(TaskSpec.of("T-FILL-2", () -> "B")).future().get(3, TimeUnit.SECONDS));
            assertEquals(3, worker.getRegistrySize(), "应刚好占满订单板");

            // 再提交：先删已终结记录腾位置（删哪条不作保证），新单正常收单
            TaskSubmission<String> accepted = worker.submit(TaskSpec.of("T-OVER", () -> "C"));
            assertTrue(accepted.accepted(), "删掉已终结记录后应能收单");
            assertEquals("C", accepted.future().get(3, TimeUnit.SECONDS));

            boolean purgedOne = (worker.getRecord("T-FILL-1") == null) ^ (worker.getRecord("T-FILL-2") == null);
            assertTrue(purgedOne, "应恰好删除一条已终结记录");
            assertNotNull(worker.getRecord("T-HOLD"), "未终结记录绝不能被删除");
            assertEquals(3, worker.getRegistrySize());

            // 在途单据的重复提交不新增记录，即使订单板已满也必须放行
            TaskSubmission<Void> duplicate = worker.submit(TaskSpec.of("T-HOLD", () -> {
            }));
            assertTrue(duplicate.duplicate());
            assertNotNull(duplicate.future());

            release.countDown();
            assertNull(hold.future().get(3, TimeUnit.SECONDS), "无返回值任务的成功结果为 null");
            assertEquals(TaskState.SUCCEEDED, worker.getRecord("T-HOLD").state());
        }
    }

    @Test
    @DisplayName("订单板满且全部单据在途时才拒收新单")
    void testRejectsOnlyWhenAllRecordsInFlight() throws Exception {
        // 1 个厨师 + 订单板容量 2：在岗 1 张 + 排队 1 张即占满，且无已终结记录可删
        try (WorkerUtil worker = new WorkerUtil(1, 10, 2)) {
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);

            TaskSubmission<Void> hold = worker.submit(TaskSpec.of("T-HOLD", () -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
            }));
            assertTrue(hold.accepted());
            assertTrue(started.await(3, TimeUnit.SECONDS));

            TaskSubmission<String> queued = worker.submit(TaskSpec.of("T-WAIT", () -> "W"));
            assertTrue(queued.accepted());
            assertEquals(2, worker.getRegistrySize(), "在岗 + 排队已占满订单板");

            TaskSubmission<String> rejected = worker.submit(TaskSpec.of("T-OVER", () -> "C"));
            assertFalse(rejected.accepted());
            assertEquals("订单板已满", rejected.reason());
            assertNull(worker.getRecord("T-OVER"), "被拒收的单据不得在订单板上留下记录");
            ExecutionException ex = assertThrows(ExecutionException.class,
                    () -> rejected.future().get(1, TimeUnit.SECONDS));
            assertTrue(ex.getCause() instanceof TaskNotStartedException, "拒收一律是“从未执行”，与排队区满统一标记");

            // 全部结单后（已有已终结记录可删），新单重新可接纳
            release.countDown();
            assertNull(hold.future().get(3, TimeUnit.SECONDS));
            assertEquals("W", queued.future().get(3, TimeUnit.SECONDS));
            assertTrue(worker.submit(TaskSpec.of("T-AFTER", () -> "D")).accepted(),
                    "有已终结记录可删除时应重新收单");
        }
    }

    @Test
    @DisplayName("优雅打烊：已收单据全部执行完毕，之后提交被拒")
    void testDrainAndShutdown() throws Exception {
        WorkerUtil worker = new WorkerUtil(2, 20);
        int totalTasks = 10;
        AtomicInteger processed = new AtomicInteger(0);

        for (int i = 0; i < totalTasks; i++) {
            worker.submit(TaskSpec.of("T-DRAIN-" + i, () -> {
                Thread.sleep(50L);
                return processed.incrementAndGet();
            }));
        }

        List<TaskSpec<?>> unexecuted = worker.drainAndShutdown(5, TimeUnit.SECONDS);
        assertEquals(0, unexecuted.size(), "规定时间内所有已收单据都应执行完成");
        assertEquals(totalTasks, processed.get());
        assertEquals(0, worker.getPendingFutureCount(), "关闭后不得残留未终结句柄");

        TaskSubmission<Void> rejected = worker.submit(TaskSpec.of("T-AFTER-CLOSE", () -> {
        }));
        assertFalse(rejected.accepted());
        assertEquals("车间已打烊", rejected.reason());
    }

    @Test
    @DisplayName("打烊可重复调用：后续调用直接返回空列表")
    void testDrainIsIdempotent() {
        WorkerUtil worker = new WorkerUtil(2, 10);
        assertTrue(worker.drainAndShutdown(1, TimeUnit.SECONDS).isEmpty());
        assertTrue(worker.drainAndShutdown(1, TimeUnit.SECONDS).isEmpty());
    }

    @Test
    @DisplayName("打烊超时：排队单据被放弃、在途单据被强制终结，不残留未终结记录")
    void testDrainTimeoutAbandonsUnfinished() throws Exception {
        WorkerUtil worker = new WorkerUtil(1, 4);
        CountDownLatch started = new CountDownLatch(1);

        TaskSubmission<String> running = worker.submit(TaskSpec.of("T-RUN", () -> {
            started.countDown();
            Thread.sleep(800L);
            return "RUN-DONE";
        }));
        assertTrue(running.accepted());
        assertTrue(started.await(3, TimeUnit.SECONDS));

        TaskSubmission<String> queued = worker.submit(TaskSpec.of("T-QUEUED", () -> "WAIT-DONE"));
        assertTrue(queued.accepted());

        List<TaskSpec<?>> unexecuted = worker.drainAndShutdown(100, TimeUnit.MILLISECONDS);
        assertEquals(1, unexecuted.size(), "只应放弃排队中的单据，实际: " + unexecuted);
        assertEquals("T-QUEUED", unexecuted.get(0).id());

        assertEquals(TaskState.FAILED, worker.getRecord("T-QUEUED").state(), "排队中被放弃 → 结论已知：没做过");
        assertEquals(TaskNotStartedException.class.getName(), worker.getRecord("T-QUEUED").error().type(),
                "打烊放弃 = 从未执行，与“业务失败”靠 cause 类型区分");
        ExecutionException queuedEx = assertThrows(ExecutionException.class,
                () -> queued.future().get(1, TimeUnit.SECONDS));
        assertTrue(queuedEx.getCause() instanceof TaskNotStartedException);

        // 老板跑在 Thread.sleep 上，中断以 InterruptedException 的形式抛了出来 → 拿到了“它抛了”这个结论，是 FAILED
        assertEquals(TaskState.FAILED, worker.getRecord("T-RUN").state(),
                "中断让业务抛了异常 → 结论已知（失败），应是 FAILED");
        assertThrows(ExecutionException.class, () -> running.future().get(1, TimeUnit.SECONDS));
        assertEquals(0, worker.getPendingFutureCount(), "关闭返回后不得残留未终结句柄");
    }

    @Test
    @DisplayName("在途被打断且拿不到业务结论 → UNKNOWN（重投必须幂等）")
    void testInterruptedInFlightBecomesUnknown() throws Exception {
        WorkerUtil worker = new WorkerUtil(1, 4);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        TaskSubmission<String> running = worker.submit(TaskSpec.of("T-IGNORE-INT", () -> {
            started.countDown();
            // 故意吞掉中断：业务既没返回也没抛，打烊时根本不知道它到底会成还是会败
            while (true) {
                try {
                    release.await();
                    break;
                } catch (InterruptedException ignored) {
                    // 不恢复中断标志，继续等
                }
            }
            return "LATE-RESULT";
        }));
        assertTrue(running.accepted());
        assertTrue(started.await(3, TimeUnit.SECONDS));

        worker.drainAndShutdown(100, TimeUnit.MILLISECONDS);

        assertEquals(TaskState.UNKNOWN, worker.getRecord("T-IGNORE-INT").state(),
                "action 还没返回就被中止 → 不知道成没成，必须记 UNKNOWN 而不是 FAILED");
        assertThrows(ExecutionException.class, () -> running.future().get(1, TimeUnit.SECONDS));

        release.countDown(); // 放行：让它跑完，尝试“事后翻案”
        Thread.sleep(200L);
        assertEquals(TaskState.UNKNOWN, worker.getRecord("T-IGNORE-INT").state(),
                "已被收口判死的记录不得被事后改写，否则记录说成功、调用方却拿到失败");
    }

    @Test
    @DisplayName("打烊与提交互斥：关闭返回后不留未终结记录，所有句柄都已完结")
    void testNoUnsettledRecordAfterShutdown() throws Exception {
        for (int round = 0; round < 20; round++) {
            WorkerUtil worker = new WorkerUtil(2, 50);
            List<TaskSubmission<String>> submissions = Collections.synchronizedList(new ArrayList<>());
            List<Thread> submitters = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger seq = new AtomicInteger();

            for (int i = 0; i < 4; i++) {
                Thread thread = new Thread(() -> {
                    try {
                        start.await();
                        for (int k = 0; k < 20; k++) {
                            submissions.add(worker.submit(TaskSpec.of("S-" + seq.incrementAndGet(), () -> {
                                Thread.sleep(1L);
                                return "ok";
                            })));
                        }
                    } catch (InterruptedException e) {
                        log.error("提交线程被中断", e);
                        Thread.currentThread().interrupt();
                    }
                });
                submitters.add(thread);
                thread.start();
            }

            start.countDown();
            Thread.sleep(5L); // 让提交先跑起来，制造“关闭期间仍在提交”的场景
            worker.drainAndShutdown(2, TimeUnit.SECONDS);
            for (Thread thread : submitters) {
                thread.join(5_000L);
            }

            assertEquals(0, worker.getPendingFutureCount(),
                    "关闭返回后不得残留未终结句柄（第 " + round + " 轮）");
            for (TaskSubmission<String> submission : submissions) {
                assertTrue(submission.future().isDone(),
                        "关闭返回后每个句柄都必须已完结（第 " + round + " 轮）");
            }
        }
    }

}
