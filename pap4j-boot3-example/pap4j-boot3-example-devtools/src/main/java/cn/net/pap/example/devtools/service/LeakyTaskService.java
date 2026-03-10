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

    /**
     * 在 Spring 容器关闭时触发的清理操作，主要用于优雅地关闭底层线程池并处理未执行的任务。
     * <p>
     * <b>关于 @PreDestroy 执行优先级与 Spring Bean 销毁顺序的架构说明：</b>
     * <ul>
     * <li><b>核心原则（逆向依赖销毁）：</b> Spring 遵循“依赖者先销毁，被依赖者后销毁”的原则。
     * 当前 Service 的销毁过程（即本方法的执行）会在它所依赖的外部 Bean 销毁之前进行。</li>
     * <li><b>显式注入的安全性：</b> 在线程池关闭的阻塞等待期间（如 {@code awaitTermination}），
     * 如果排队的任务仍在继续执行并调用了其他的 Spring Bean（如 Repository、Redis 等），
     * <b>前提是这些 Bean 必须是通过构造器或 {@code @Autowired} 显式注入到当前类的</b>。
     * Spring 会保证在当前方法彻底执行完毕前，这些被依赖的组件依然存活且可用。</li>
     * <li><b>隐式依赖的致命隐患：</b> 严禁在未结束的任务中通过 {@code ApplicationContext.getBean()} 动态获取 Bean，
     * 或调用持有 Spring Bean 的静态上下文工具类。由于 Spring 无法感知此类隐式依赖，
     * 极有可能在调用本方法之前就已经将目标 Bean 销毁，从而引发不可预期的 {@code Context Closed} 或空指针异常。</li>
     * <li><b>无依赖 Bean 的随机“死亡”风险：</b> 如果系统中存在与当前 Service 没有明确注入关系的独立 Bean（即“同级 Bean”），
     * Spring 销毁它们的先后顺序是<b>完全不确定</b>的。这意味着在线程池等待关闭的缓冲期内，如果任务尝试访问这些无依赖的 Bean，
     * 它们极有可能已经先一步被 Spring 销毁了，从而导致应用报错。如果任务确实需要依赖某个独立的 Bean 存活到最后，
     * 必须在当前类上使用 {@code @DependsOn("目标Bean名称")} 来强制让 Spring 先销毁当前 Service。</li>
     * </ul>
     * <p>
     * <b>架构演进建议：使用 SmartLifecycle 进行降维控制</b>
     * <p>
     * 当前使用 {@code @PreDestroy} 属于“被动清理”，是在 Spring 按照依赖关系逐个销毁 Bean 时触发的。
     * 鉴于本类内部包含持续运行的后台引擎（{@code while} 死循环监控），它本质上是一个“任务源头”。
     * 为了获得绝对的停机控制权，建议未来将其改造为实现 Spring 的 {@code SmartLifecycle} 接口：
     * <ul>
     * <li><b>绝对优先的执行顺序：</b> 当系统收到关闭信号时，Spring 会<b>首先</b>冻结全局状态并触发所有 {@code SmartLifecycle} 的 {@code stop()} 方法。
     * 等待它们全部停稳后，<b>才会</b>开始按依赖关系销毁普通的 Spring Bean（即触发其他类的 {@code @PreDestroy}）。</li>
     * <li><b>全局统筹的最优解：</b> 通过 {@code SmartLifecycle}，可以在所有业务组件（如数据库、缓存连接等）开始销毁之前，
     * 第一时间强制掐断本类的循环监控，拒绝新任务的产生。这能从根本上避免“前台还在接客，后厨已经下班”的严重资源关闭报错。</li>
     * <li><b>Phase 阶段控制：</b> 实现该接口后，可以通过重写 {@code getPhase()} 方法（例如返回 {@code Integer.MAX_VALUE}），
     * 精细且强硬地定义当前组件在全局启停过程中的绝对顺位。</li>
     * </ul>
     */
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