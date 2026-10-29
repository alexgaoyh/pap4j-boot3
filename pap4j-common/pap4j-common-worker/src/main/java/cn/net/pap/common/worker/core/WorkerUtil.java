package cn.net.pap.common.worker.core;

import cn.net.pap.common.core.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;

/**
 * 进程内异步任务车间（最小实现）：店长不炒菜，厨师不管排队，订单板是唯一真相。
 *
 * <p><b>职责</b>：收单 → 并发执行 → 状态记账 → 优雅关闭。不做重试、不做延迟调度、不含任何时间概念。</p>
 *
 * <p><b>提交三态</b>：收单（ACCEPTED）、在途重复（DUPLICATE）、拒收（REJECTED：
 * 车间已打烊 / 排队区已满 / 订单板已满）。</p>
 *
 * <p><b>容量</b>：排队区有界，满则拒收；订单板有界，满时先删除<b>已终结</b>
 * （{@link TaskState#SUCCEEDED} / {@link TaskState#FAILED}）记录腾位置，无已终结记录可删则拒收。
 * <b>未终结记录永不被删除</b>，因此 {@link #getRecord(String)} 只保证“当前仍保留”。</p>
 *
 * <p><b>关闭</b>：拒新单 → 队尾投递哨兵 → 等待存量单据执行完毕；超时则放弃排队单据、强制中断在途任务。
 * 返回后订单板上不存在未终结记录。</p>
 *
 * <p><b>为什么需要一把锁</b>：“提交”与“打烊”必须互斥：{@code submit} 的登记+入队与关闭流程的
 * “关闭线程池”互斥后，才能保证一旦线程池 shutDown 就不会再产生新记录，关闭收口因此可以断定
 * “扫描到的就是全部”。锁内只有纯内存操作，不阻塞、不做 I/O。</p>
 *
 * <p><b>限制</b>：进程内实现，重启即丢；不支持取消与执行超时；订单板是定容缓存而非审计表。</p>
 */
public class WorkerUtil implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkerUtil.class);

    /**
     * 默认订单板容量。
     */
    private static final int DEFAULT_MAX_RECORDS = 10_000;

    /**
     * 关闭哨兵：投递到队尾通知厨师下班（FIFO 保证它排在所有真实任务之后）。
     */
    private static final TaskSpec<?> STOP = new TaskSpec<>("__worker_stop__", () -> null);

    /**
     * 订单板（唯一事实源）：任务 ID -> 不可变状态快照。
     */
    private final ConcurrentHashMap<String, TaskRecord> registry = new ConcurrentHashMap<>();

    /**
     * 在途任务的结果句柄：进入终态时移除。
     */
    private final ConcurrentHashMap<String, CompletableFuture<?>> futures = new ConcurrentHashMap<>();

    /**
     * 排队区：有界 FIFO，满则提交快速失败。
     */
    private final ArrayBlockingQueue<TaskSpec<?>> queue;

    /**
     * 厨师线程池：常驻线程循环“取单 - 执行 - 结单”。
     */
    private final ThreadPoolExecutor workers;

    /**
     * 订单板容量。
     */
    private final int maxRecords;

    /**
     * 提交与打烊的互斥锁：锁内只有纯内存操作，保证“打烊之后不会再产生新记录”。
     * <p>打烊标志不单独用字段保存：{@code workers.isShutdown()} 就是它（打烊本来就必须 shutdown）。</p>
     */
    private final ReentrantLock dispatch = new ReentrantLock();

    /**
     * 指定厨师数与排队区容量，订单板使用默认容量。
     *
     * @param workerCount   常驻厨师线程数
     * @param queueCapacity 排队区容量
     */
    public WorkerUtil(int workerCount, int queueCapacity) {
        this(workerCount, queueCapacity, DEFAULT_MAX_RECORDS);
    }

    /**
     * 全参构造方法。
     *
     * @param workerCount   常驻厨师线程数
     * @param queueCapacity 排队区容量
     * @param maxRecords    订单板容量
     */
    public WorkerUtil(int workerCount, int queueCapacity, int maxRecords) {
        if (workerCount <= 0 || queueCapacity <= 0 || maxRecords <= 0) {
            throw new IllegalArgumentException("workerCount, queueCapacity and maxRecords must be greater than 0");
        }
        this.maxRecords = maxRecords;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);

        // 常驻厨师：池内队列只承载启动阶段的初始任务，满了直接抛错而不静默丢弃
        this.workers = new ThreadPoolExecutor(workerCount, workerCount, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(workerCount),
                new NamedThreadFactory("worker-chef-", 1),
                new ThreadPoolExecutor.AbortPolicy());
        for (int i = 0; i < workerCount; i++) {
            this.workers.execute(this::workerLoop);
        }
    }

    /**
     * 提交任务（三态返回）。
     *
     * <p>同一 id 在途时重复提交会返回既有句柄且不重复执行；该 id 已终结或记录已被清理时视为新任务。</p>
     *
     * @param <T>  任务返回值类型
     * @param spec 任务规格
     * @return 提交响应
     * @throws IllegalArgumentException 任务规格为 null，或 id 为空白
     */
    public <T> TaskSubmission<T> submit(TaskSpec<T> spec) {
        requireValid(spec);

        // 与关闭互斥：锁外不会出现“已打烊却仍登记新记录”的情况
        dispatch.lock();
        try {
            if (workers.isShutdown()) { // 打烊标志 = 线程池已关闭
                return reject("车间已打烊", new TaskNotStartedException("车间已打烊，拒绝接收新任务"));
            }
            if (!hasRoomFor(spec.id())) {
                return reject("订单板已满", new TaskNotStartedException(
                        "订单板已满（上限 " + maxRecords + "），拒绝接收新任务"));
            }

            Reservation<T> seat = reserve(spec.id());
            if (!seat.created()) {
                return TaskSubmission.duplicate(seat.future()); // 在途重复：复用既有句柄
            }
            if (!queue.offer(spec)) {
                fail(spec.id(), new TaskNotStartedException("排队区已满"));
                return TaskSubmission.rejected("排队区已满", seat.future());
            }
            return TaskSubmission.accepted(seat.future());
        } finally {
            dispatch.unlock();
        }
    }

    /**
     * 参数校验（编程错误，不是业务拒收）。
     *
     * @param spec 任务规格
     */
    private static void requireValid(TaskSpec<?> spec) {
        if (spec == null) {
            throw new IllegalArgumentException("TaskSpec must not be null");
        }
        if (spec.id() == null || spec.id().isBlank()) {
            throw new IllegalArgumentException("TaskSpec.id must not be blank");
        }
    }

    /**
     * 订单板是否还能容纳该 id 的新记录：在途 id 不新增记录直接放行；板满则先回收已终结记录再判断。
     *
     * @param id 任务 ID
     * @return true 表示可以接纳本次提交
     */
    private boolean hasRoomFor(String id) {
        TaskRecord current = registry.get(id);
        if (current != null && !current.terminal()) {
            return true;
        }
        if (registry.size() < maxRecords) {
            return true;
        }
        recycleRecords();
        return registry.size() < maxRecords;
    }

    /**
     * 占座：该 id 在途则返回其既有句柄；否则创建并登记新句柄（只有真新建时才分配句柄）。
     *
     * @param <T> 任务返回值类型
     * @param id  任务 ID
     * @return 占座结果（句柄 + 是否本次新建）
     */
    @SuppressWarnings("unchecked") // 句柄表存的是 CompletableFuture<?>，交付给调用方时只能强转为 T（泛型已擦除）
    private <T> Reservation<T> reserve(String id) {
        CompletableFuture<?>[] seat = new CompletableFuture<?>[1];
        boolean[] created = new boolean[1];
        // 占座与句柄登记必须同块完成，否则并发提交可能看到记录却取不到句柄
        registry.compute(id, (key, existing) -> {
            if (existing != null && !existing.terminal()) {
                seat[0] = futures.get(key);
                return existing;
            }
            CompletableFuture<T> future = new CompletableFuture<>();
            created[0] = true;
            seat[0] = future;
            futures.put(key, future);
            return TaskRecord.queued(id);
        });
        return new Reservation<>((CompletableFuture<T>) seat[0], created[0]);
    }

    /**
     * 查询任务在订单板上的事实记录。
     *
     * @param id 任务 ID
     * @return 记录快照；不存在或已因容量清理被删除时返回 null
     */
    public TaskRecord getRecord(String id) {
        return registry.get(id);
    }

    /**
     * 当前排队区积压任务数（含关闭阶段的哨兵）。
     *
     * @return 排队区任务数
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 订单板当前记录数（含已终结但尚未被清理的记录）。
     *
     * @return 订单板记录数
     */
    public int getRegistrySize() {
        return registry.size();
    }

    /**
     * 当前在途任务数。
     *
     * @return 尚未终结的结果句柄数
     */
    public int getPendingFutureCount() {
        return futures.size();
    }

    /**
     * 优雅打烊：拒新单，等待存量单据执行完毕，超时则放弃未完成单据。可重复调用。
     *
     * @param timeout 超时时长数值
     * @param unit    时间单位
     * @return 被放弃（标记为 FAILED）的任务规格；无超时或重复调用时返回空列表
     */
    public List<TaskSpec<?>> drainAndShutdown(long timeout, TimeUnit unit) {
        // 与提交互斥：拿到锁后关闭线程池（= 挂打烊牌），此后不可能再产生新记录
        dispatch.lock();
        try {
            if (workers.isShutdown()) {
                return List.of();
            }
            workers.shutdown();
        } finally {
            dispatch.unlock();
        }

        // 1. 队尾投递哨兵：厨师先执行完所有真实任务，再依次取到哨兵下班
        for (int i = 0; i < workers.getMaximumPoolSize(); i++) {
            if (!queue.offer(STOP)) {
                break; // 排队区已满：由超时分支兜底
            }
        }

        // 2. 等待存量单据执行完毕
        boolean terminated = awaitTermination(timeout, unit);

        // 3. 超时：放弃排队中的单据（确定没跑），强制中断在途任务（结果未知）
        TaskNotStartedException notStarted = new TaskNotStartedException("打烊超时未执行");
        RejectedExecutionException unknown = new RejectedExecutionException("打烊超时未完成（结果未知）");
        List<TaskSpec<?>> unexecuted = new ArrayList<>();
        if (!terminated) {
            List<TaskSpec<?>> leftover = new ArrayList<>();
            queue.drainTo(leftover);
            for (TaskSpec<?> spec : leftover) {
                if (spec != STOP && abandon(spec, notStarted)) {
                    unexecuted.add(spec);
                }
            }
            workers.shutdownNow();
            awaitTermination(1, TimeUnit.SECONDS);
        }

        // 4. 收口：关闭返回后不允许订单板残留未终结记录
        List<String> forced = new ArrayList<>();
        for (TaskRecord record : registry.values()) {
            if (!record.terminal()) {
                markUnknown(record.id(), unknown);
                forced.add(record.id());
            }
        }
        if (!forced.isEmpty()) {
            log.warn("打烊收口：{} 张未终结单据已被标记为 UNKNOWN（结果未知），全部: {}", forced.size(), forced);
        }
        return unexecuted;
    }

    @Override
    public void close() {
        List<TaskSpec<?>> unexecuted = drainAndShutdown(5, TimeUnit.SECONDS);
        if (!unexecuted.isEmpty()) {
            log.warn("打烊完成：{} 张未终结单据已被标记为 FAILED，全部: {}", unexecuted.size(), unexecuted);
        }
    }

    /**
     * 厨师主循环：阻塞取单（无轮询），取到哨兵即下班。
     */
    private void workerLoop() {
        String workerName = Thread.currentThread().getName();
        while (!Thread.currentThread().isInterrupted()) {
            TaskSpec<?> spec;
            try {
                spec = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("厨师线程 [{}] 被中断退出", workerName, e);
                return;
            }
            if (spec == STOP) {
                return;
            }
            try {
                execute(spec);
            } catch (Throwable t) {
                // 任何意外都不能让厨师倒下，否则该单据的句柄将永远无法完成
                log.error("厨师 [{}] 处理任务 [{}] 时发生未预期异常", workerName, spec.id(), t);
            }
        }
    }

    /**
     * 执行单张单据：认领（RUNNING）→ 调用业务逻辑 → 落终态并完成句柄。
     *
     * @param spec 任务规格
     */
    private void execute(TaskSpec<?> spec) {
        // 只允许从 QUEUED 认领：终态（已被放弃/清理）与 RUNNING（已被其他厨师认领）都不可执行
        TaskRecord running = transition(spec.id(),
                record -> record.state() == TaskState.QUEUED ? record.toRunning() : record);
        if (running == null || running.state() != TaskState.RUNNING) {
            return;
        }

        CompletableFuture<?> future = futures.get(spec.id());
        try {
            Object result = spec.action().call();
            settle(spec.id(), record -> record.toSucceeded(result));
            complete(future, result);
        } catch (Throwable t) {
            log.error("任务 [{}] 执行失败: {}", spec.id(), t.getMessage(), t);
            try {
                settle(spec.id(), record -> record.toFailed(t));
            } catch (Throwable snapshotError) {
                // 连“记下失败原因”这一步都失败了（异常本身读不出来）。
                // 单子不能就这么一直挂在“执行中”，退一步：直接判它失败，只是不带异常信息。
                log.error("任务 [{}] 的失败原因记不下来，直接判 FAILED（不带异常信息）", spec.id(), snapshotError);
                settle(spec.id(), record -> record.toFailed(null));
            }
            completeExceptionally(future, t);
        } finally {
            // CompletableFuture 契约兜底：非取消的句柄必须完成，否则调用方永久挂死
            if (future != null && !future.isDone()) {
                IllegalStateException unsettled = new IllegalStateException("任务 [" + spec.id() + "] 未能落定终态");
                // 连业务结论都没拿到，记录不能留在“执行中”：落 UNKNOWN（结果未知）
                settle(spec.id(), record -> record.toUnknown(unsettled));
                completeExceptionally(future, unsettled);
            }
        }
    }

    /**
     * 放弃一张仍在排队的单据（已在执行或已终结的不动）。
     *
     * @param spec  任务规格
     * @param cause 放弃原因
     * @return true 表示本次调用真的把它置为 FAILED
     */
    private boolean abandon(TaskSpec<?> spec, Throwable cause) {
        CompletableFuture<?> future = futures.get(spec.id());
        AtomicBoolean applied = new AtomicBoolean(false);
        transition(spec.id(), record -> {
            if (record.state() != TaskState.QUEUED) {
                return record;
            }
            applied.set(true);
            return record.toFailed(cause);
        });
        if (!applied.get()) {
            return false;
        }
        completeExceptionally(future, cause);
        return true;
    }

    /**
     * 把指定单据置为 FAILED 并让句柄异常完成。
     *
     * @param id    任务 ID
     * @param cause 失败原因
     */
    private void fail(String id, Throwable cause) {
        CompletableFuture<?> future = futures.get(id);
        transition(id, record -> record.toFailed(cause));
        completeExceptionally(future, cause);
    }

    /**
     * 把任意非终态单据置为 {@link TaskState#UNKNOWN} 并让句柄异常完成：
     * “不知道它到底成没成”（在途被打断、没拿到业务结论）。
     *
     * @param id    任务 ID
     * @param cause 中止原因
     */
    private void markUnknown(String id, Throwable cause) {
        CompletableFuture<?> future = futures.get(id);
        transition(id, record -> record.toUnknown(cause));
        completeExceptionally(future, cause);
    }

    /**
     * 订单板满时删除已终结记录，删到低于上限即止（顺序不作保证）。
     */
    private void recycleRecords() {
        for (TaskRecord record : registry.values()) {
            if (registry.size() < maxRecords) {
                return;
            }
            if (record.terminal()) {
                registry.remove(record.id(), record);
            }
        }
    }

    /**
     * 等待厨师线程池终止。
     *
     * @param timeout 超时时长数值
     * @param unit    时间单位
     * @return true 表示已终止
     */
    private boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            return workers.awaitTermination(timeout, unit);
        } catch (InterruptedException e) {
            log.error("等待厨师线程池终止被中断", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 结账：只有仍处于 {@link TaskState#RUNNING} 的单据才允许落终态。
     *
     * <p>已被打烊收口判死（{@code UNKNOWN}）或提前判失败的记录一律原样保留；
     * 否则跑飞的任务会把“关闭时已对调用方宣告失败”的单据事后翻成成功，
     * 出现“记录说成功、句柄说失败”的不一致。</p>
     *
     * @param id         任务 ID
     * @param toTerminal 终态迁移（仅在当前为 RUNNING 时生效）
     * @return 迁移后的记录；未生效时为原记录
     */
    private TaskRecord settle(String id, UnaryOperator<TaskRecord> toTerminal) {
        return transition(id, record -> record.state() == TaskState.RUNNING ? toTerminal.apply(record) : record);
    }

    /**
     * 订单板原子状态迁移：迁移到终态时同步移除结果句柄。
     *
     * @param id       任务 ID
     * @param operator 状态迁移函数
     * @return 迁移后的记录快照；记录不存在时返回 null
     */
    private TaskRecord transition(String id, UnaryOperator<TaskRecord> operator) {
        return registry.computeIfPresent(id, (key, current) -> {
            TaskRecord next = operator.apply(current);
            if (next != null && next.terminal()) {
                futures.remove(key);
            }
            return next;
        });
    }

    /**
     * 构造拒收响应（句柄已异常完成）。
     *
     * @param <T>    任务返回值类型
     * @param reason 拒收原因
     * @param cause  异常原因
     * @return 拒收响应
     */
    private static <T> TaskSubmission<T> reject(String reason, Throwable cause) {
        return TaskSubmission.rejected(reason, CompletableFuture.failedFuture(cause));
    }

    private static void complete(CompletableFuture<?> future, Object result) {
        if (future == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        CompletableFuture<Object> typed = (CompletableFuture<Object>) future;
        typed.complete(result);
    }

    private static void completeExceptionally(CompletableFuture<?> future, Throwable cause) {
        if (future != null) {
            future.completeExceptionally(cause);
        }
    }

}
