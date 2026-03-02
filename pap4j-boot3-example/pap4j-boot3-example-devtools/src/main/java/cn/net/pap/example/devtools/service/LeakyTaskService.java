package cn.net.pap.example.devtools.service;

import cn.net.pap.example.devtools.executor.PapIdentifiedThreadPoolExecutor;
import cn.net.pap.example.devtools.task.PapIdentifiedFutureTask;
import cn.net.pap.example.devtools.task.PapIdentifiedTask;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 演示和测试在 Spring Boot 应用关闭时，如何优雅地处理线程池中未执行完毕的任务，并提取任务的业务标识（ID）以便进行后续的补偿操作。
 */
@Service
public class LeakyTaskService {

    // 【改造点 1：使用自定义线程池】
    // 这里的 corePoolSize, maximumPoolSize 两个参数，可以修改一下，从而允许多个任务提交后立即开始并行执行，比如两个值都改为2，那么就是2个任务同时运行。
    private final PapIdentifiedThreadPoolExecutor executorService = new PapIdentifiedThreadPoolExecutor(
            1, // corePoolSize
            1, // maximumPoolSize
            0L, TimeUnit.MILLISECONDS,
            // 使用 LinkedBlockingQueue 配合单线程，第二个任务会被放入队列
            new LinkedBlockingQueue<>()
    );

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    @PostConstruct
    public void init() {
        System.out.println(">>> LeakyTaskService 初始化, 线程池 Hash: " + executorService.hashCode());

        try {
            // --- 任务 1: 死循环监控任务 (使用 PapIdentifiedTask 包装) ---
            String monitorId = "SYSTEM-MONITOR-001";
            Runnable monitorTask = () -> {
                while (!Thread.currentThread().isInterrupted()) {
                    // 任务运行时可打印出 ID
                    System.out.println(">>> 线程 [" + Thread.currentThread().getName() + "] 正在运行, ID: " + monitorId + ", 线程池 Hash: " + executorService.hashCode());
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        System.out.println(">>> 线程 [" + Thread.currentThread().getName() + "] 捕获中断信号，准备退出。, 线程池 Hash: " + executorService.hashCode());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                System.out.println(">>> 线程 [" + Thread.currentThread().getName() + "] 已停止运行。, 线程池 Hash: " + executorService.hashCode());
            };

            PapIdentifiedTask identifiedMonitorTask = new PapIdentifiedTask(monitorId, monitorTask);
            executorService.submit(identifiedMonitorTask); // 使用 submit，返回 Future 对象

            // --- 任务 2: 交易任务 (使用 PapIdentifiedTask 包装，将被放入队列中排队) ---
            Runnable actualTask2 = () -> System.out.println("我是任务2，实际执行中。, 线程池 Hash: " + executorService.hashCode());
            String transactionId = "TXN-20251214-001";
            PapIdentifiedTask identifiedTask2 = new PapIdentifiedTask(transactionId, actualTask2);

            executorService.submit(identifiedTask2); // 使用 submit，返回 Future 对象

        } catch (RejectedExecutionException e) {
            System.err.println(">>> 任务提交失败：线程池已关闭。, 线程池 Hash: " + executorService.hashCode());
        }
    }

    @PreDestroy
    public void shutdown() {
        System.out.println(">>> LeakyTaskService 正在关闭线程池..., 线程池 Hash: " + executorService.hashCode());

        // 1. 强制关闭，队列中的任务（IdentifiedFutureTask）被退回
        List<Runnable> skippedTasks = executorService.shutdownNow();

        if (!skippedTasks.isEmpty()) {
            System.out.println(">>> 注意：有 " + skippedTasks.size() + " 个排队任务未被执行。, 线程池 Hash: " + executorService.hashCode());

            for (Runnable task : skippedTasks) {
                // 2. 安全地从 IdentifiedFutureTask 中提取原始任务的 ID
                // 队列中的元素是 IdentifiedFutureTask，可以安全转型
                if (task instanceof PapIdentifiedFutureTask<?> identifiedFuture) {
                    PapIdentifiedTask originalTask = identifiedFuture.getOriginalTask();
                    System.out.println(">>>>>> 任务标识： " + originalTask.getTaskId() + " - 需要补偿处理！, 线程池 Hash: " + executorService.hashCode());
                } else {
                    System.out.println(">>>>>> 警告：发现非 PapIdentifiedTask 包装的任务类型，无法获取标识符。, 线程池 Hash: \" + executorService.hashCode()");
                }
            }
        } else {
            System.out.println(">>> 当前没有排队任务。, 线程池 Hash: " + executorService.hashCode());
        }

        try {
            // 3. 等待线程响应中断
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.err.println(">>> [警告] 线程池未能在 " + SHUTDOWN_TIMEOUT_SECONDS + "s 内结束！, 线程池 Hash: " + executorService.hashCode());
            } else {
                System.out.println(">>> 线程池已成功关闭，资源已释放。, 线程池 Hash: " + executorService.hashCode());
            }
        } catch (InterruptedException e) {
            System.err.println(">>> 线程池关闭过程被外部中断！, 线程池 Hash: " + executorService.hashCode());
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

}