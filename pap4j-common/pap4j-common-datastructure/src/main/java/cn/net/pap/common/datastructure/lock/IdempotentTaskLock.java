package cn.net.pap.common.datastructure.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 幂等任务锁
 * 核心机制：基于 ConcurrentHashMap 桶锁 + 状态机 + 惰性删除 + 外部注入定时器扫表兜底。
 * * 【使用说明】
 * 1. 业务层必须在应用启动时调用 {@link #init(ScheduledExecutorService)} 注入定时调度器，否则无法清理僵尸锁。
 * 2. 业务层在应用关闭时，只需销毁传入的 ScheduledExecutorService，并按需调用 {@link #clear()} 即可。
 */
public class IdempotentTaskLock {

    private static final Logger log = LoggerFactory.getLogger(IdempotentTaskLock.class);

    private enum State {
        PENDING(0), RUNNING(1), COMPLETED(2);
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

        // 记录的是释放锁后的冷却截止时间。用于防重复提交（比如释放后 60 秒内不许再请求）。初始为 0L 仅仅是个无意义的占位符，因为处于 RUNNING 状态的锁，根本不会去校验 isExpired()。
        volatile long expireTimeNano;

        // 改为 final，在对象创建时死死绑定。彻底杜绝 CAS 期间 CPU 切换导致的僵尸锁误杀。记录的是加锁时间。用于卡死异常时的强制回收兜底（上限 10 分钟）。
        final long startRunTimeNano;

        Status(State initialState) {
            this.state = new AtomicReference<>(initialState);
            this.expireTimeNano = 0L;
            this.startRunTimeNano = System.nanoTime();
        }

        boolean release(boolean success, long keepAliveNanos) {
            if (success) {
                // 利用 Happens-Before 原则，先写普通 volatile，再写 Atomic，保证可见性
                this.expireTimeNano = System.nanoTime() + keepAliveNanos;
                return state.compareAndSet(State.RUNNING, State.COMPLETED);
            } else {
                return state.compareAndSet(State.RUNNING, State.PENDING);
            }
        }

        State getState() {
            return state.get();
        }

        boolean isExpired() {
            return System.nanoTime() - expireTimeNano > 0;
        }
    }

    private static final ConcurrentHashMap<String, Status> accessCounts = new ConcurrentHashMap<>();

    // 默认防重冷却时间（秒）
    private static final long DEFAULT_KEEP_ALIVE_SECONDS = 60;

    // 异常 RUNNING 状态的极限兜底超时时间（分钟），超过此时间将被强制回收防死锁
    private static final long MAX_RUNNING_MINUTES = 10;

    // 标记是否已经初始化
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    // 保证未初始化的告警日志全局只打印一次，防止磁盘被打爆
    private static final AtomicBoolean LOGGED_WARNING = new AtomicBoolean(false);

    // 屏蔽实例化
    private IdempotentTaskLock() {
    }

    /**
     * 由外部业务层/应用容器在启动时调用，注入定时调度器
     *
     * @param scheduler 外部统一管理的调度线程池
     */
    public static void init(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("调度器 scheduler 不能为空");
        }

        if (INITIALIZED.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    sweepExpiredKeys();
                } catch (Exception e) {
                    log.error("IdempotentTaskLock 全局清理异常", e);
                }
            }, 30, 30, TimeUnit.SECONDS);
            log.info("IdempotentTaskLock 初始化完成，已接入外部调度器进行后台兜底清理。");
        } else {
            log.warn("IdempotentTaskLock 已经被初始化过了，忽略本次调用。");
        }
    }

    /**
     * 尝试获取执行锁
     */
    public static boolean tryAcquire(String key) {
        if (!INITIALIZED.get()) {
            if (LOGGED_WARNING.compareAndSet(false, true)) {
                log.warn("【严重警告】IdempotentTaskLock 未调用 init() 注入调度器，将失去 OOM 防御能力！本警告仅打印一次。");
            }
        }

        final boolean[] acquired = {false};
        accessCounts.compute(key, (k, existingStatus) -> {
            // 1. 全新 Key
            if (existingStatus == null) {
                acquired[0] = true;
                return new Status(State.RUNNING);
            }

            State currentState = existingStatus.getState();

            // 2. 正常冷却期结束
            if (currentState == State.COMPLETED && existingStatus.isExpired()) {
                acquired[0] = true;
                return new Status(State.RUNNING);
            }

            // 3. 对于失败回退产生的 PENDING 状态，不再尝试复用老对象！
            // 直接原地替换为全新的 Status 对象，强制刷新 startRunTimeNano，根除并发竞态漏洞。
            if (currentState == State.PENDING) {
                acquired[0] = true;
                return new Status(State.RUNNING);
            }

            // 4. 执行中 (RUNNING) 或是 仍在冷却期内 (COMPLETED && !isExpired)
            return existingStatus;
        });
        return acquired[0];
    }

    public static boolean release(String key, boolean success) {
        return release(key, success, DEFAULT_KEEP_ALIVE_SECONDS);
    }

    public static boolean release(String key, boolean success, long keepAliveSeconds) {
        Status status = accessCounts.get(key);
        if (status == null) return false;

        boolean changed = status.release(success, TimeUnit.SECONDS.toNanos(keepAliveSeconds));

        // 业务执行失败退回 PENDING 时，尝试清理垃圾状态
        if (changed && !success) {
            accessCounts.computeIfPresent(key, (k, existingStatus) -> {
                // 基于对象引用的精确比对，防止误删其他线程刚刚新建的锁
                if (existingStatus == status && existingStatus.getState() == State.PENDING) {
                    return null;
                }
                return existingStatus;
            });
        }
        return changed;
    }

    /**
     * 安全的强制重置
     * 严禁破坏 COMPLETED 状态的防重冷却契约，仅清理残留的 PENDING 垃圾数据。
     */
    public static boolean safeForceReset(String key) {
        final boolean[] removed = {false};
        accessCounts.computeIfPresent(key, (k, v) -> {
            if (v.getState() == State.PENDING) {
                removed[0] = true;
                return null;
            }
            return v;
        });
        return removed[0];
    }

    public static int getStatus(String key) {
        Status status = accessCounts.get(key);
        return status == null ? State.PENDING.getCode() : status.getState().getCode();
    }

    /**
     * 清理内存资源，在应用销毁/重启时调用
     */
    public static void clear() {
        accessCounts.clear();
        log.info("IdempotentTaskLock 内存状态已清空。");
    }

    /**
     * 全局兜底扫表：清理过期冷却锁，绞杀超时僵尸锁
     */
    private static void sweepExpiredKeys() {
        long currentNano = System.nanoTime();
        long maxRunningNanos = TimeUnit.MINUTES.toNanos(MAX_RUNNING_MINUTES);

        for (Map.Entry<String, Status> entry : accessCounts.entrySet()) {
            Status status = entry.getValue();
            State currentState = status.getState();

            if (currentState == State.COMPLETED && status.isExpired()) {
                // 必须传入 value 进行精确比对移除，防止误杀刚复用的新锁
                accessCounts.remove(entry.getKey(), status);
            } else if (currentState == State.RUNNING && (currentNano - status.startRunTimeNano > maxRunningNanos)) {
                log.error("【警告】检测到异常的长期运行锁，触发强制回收防 OOM，Key: {}", entry.getKey());
                accessCounts.remove(entry.getKey(), status);
            }
        }
    }

}