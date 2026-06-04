package cn.net.pap.example.javafx.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * BackGroundExecutors
 */
public class BackGroundExecutors {

    private static final Logger log = LoggerFactory.getLogger(BackGroundExecutors.class);

    // 1. 将核心线程数与最大线程数设为一致，避免有界队列阻碍新线程创建的问题 对于图像处理+IO混合型任务，CPU核心数 * 2 是一个比较稳妥的配置
    private static final ExecutorService BACKGROUND_EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 2,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "app-global-bg-worker");
                // 设为守护线程，确保主线程异常退出时，JVM 能正常强制退出
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时的拒绝策略
    );

    /**
     * 获取全局后台线程池
     */
    public static ExecutorService background() {
        return BACKGROUND_EXECUTOR;
    }

    /**
     * 优雅关闭线程池（在整个 JavaFX 程序退出时调用）
     */
    public static void shutdownAll() {
        log.info("正在关闭全局后台线程池...");
        BACKGROUND_EXECUTOR.shutdown(); // 拒绝新任务提交
        try {
            // 给当前正在处理图像或写文件的任务 3 秒的缓冲时间
            if (!BACKGROUND_EXECUTOR.awaitTermination(3, TimeUnit.SECONDS)) {
                log.warn("部分后台任务未在限定时间内结束，正在强制关闭...");
                BACKGROUND_EXECUTOR.shutdownNow(); // 强行中断
            }
        } catch (InterruptedException e) {
            log.error("关闭线程池时被中断", e);
            BACKGROUND_EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}