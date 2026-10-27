package cn.net.pap.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 线程池通用操作工具类。
 * <p>提供线程池的两阶段安全优雅关闭及超时等待实现，消除各业务模块重复代码。</p>
 */
public final class ThreadPoolUtil {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolUtil.class);

    private static final long DEFAULT_TIMEOUT_SECONDS = 5L;

    private ThreadPoolUtil() {
    }

    /**
     * 优雅关闭指定的线程池，并在默认超时时间（5 秒）内等待所有已提交任务执行完毕。
     *
     * @param pool 待关闭的线程池，若为 null 则直接返回 true
     * @return 线程池是否在超时内成功终止
     */
    public static boolean shutdownAndAwaitTermination(ExecutorService pool) {
        return shutdownAndAwaitTermination(pool, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 优雅关闭指定的线程池，并在指定的超时时间内等待所有已提交任务执行完毕。
     * <p>标准的两阶段终止模式：先执行 shutdown 禁止新任务提交；若超时仍未结束，则执行 shutdownNow 尝试取消当前任务并再次等待。</p>
     *
     * @param pool    待关闭的线程池，若为 null 则直接返回 true
     * @param timeout 超时时间数值
     * @param unit    时间单位
     * @return 线程池是否在超时内成功终止，若发生中断或超时未完成则返回 false
     */
    public static boolean shutdownAndAwaitTermination(ExecutorService pool, long timeout, TimeUnit unit) {
        if (pool == null) {
            return true;
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeout, unit)) {
                log.warn("线程池未能正常完成所有任务，正在执行强制关闭: {}", pool);
                pool.shutdownNow();
                if (!pool.awaitTermination(timeout, unit)) {
                    log.error("强制关闭后线程池仍未完全终止: {}", pool);
                }
            }
            return pool.isTerminated();
        } catch (InterruptedException e) {
            log.error("等待线程池终止期间被中断，正在执行强制关闭: {}", pool, e);
            pool.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
