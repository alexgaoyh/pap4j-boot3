package cn.net.pap.common.datastructure.pipeline.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 工作流执行引擎
 */
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    // 专门用于工作流超时控制的线程池
    private static final java.util.concurrent.ThreadPoolExecutor WORKFLOW_EXECUTOR = new java.util.concurrent.ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors() * 2,
            60L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(1000),
            new java.util.concurrent.ThreadFactory() {
                private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "workflow-worker-" + counter.getAndIncrement());
                    // 设置为守护线程，避免阻止 JVM 正常停机
                    thread.setDaemon(true);
                    return thread;
                }
            },
            // 如果线程池满，交由调用方（主线程）执行，作为降级策略
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 执行工作流
     *
     * @param context 当前工作流上下文
     * @param nodes   需要按顺序执行的工作流节点
     * @return 执行结束后的工作流上下文
     */
    public static WorkflowContext execute(WorkflowContext context, WorkflowNode... nodes) {
        log.info("工作流引擎启动...");

        // 不管之前是成功（挂起后继续）、失败（系统异常重试）、还是中断（业务阻断后重试），
        // 只要再次交由引擎执行，就代表业务方希望"继续"或"重试"。
        // 因此自动将其唤醒为 RUNNING 状态，并清理历史错误记录以重新评估。
        if (context.getStatus() != WorkflowStatus.RUNNING) {
            context.setStatus(WorkflowStatus.RUNNING);
            context.setErrorNode(null);
            context.setMessage("success");
        }

        for (WorkflowNode node : nodes) {
            if (!context.canContinue()) {
                log.debug("工作流已终止，跳过后续节点: [{}]", node.name());
                break;
            }

            // 断点续传核心逻辑：如果当前节点之前已经成功执行过，则直接跳过
            // 开发者需保证业务流中每个 Node 的 name() 全局唯一
            if (context.getExecutedNodes().contains(node.name())) {
                log.info("节点 [{}] 已执行过，自动跳过 (断点续传)", node.name());
                continue;
            }

            try {
                long timeout = node.timeoutMillis();
                if (timeout > 0) {
                    log.info("节点 [{}] 开启了超时控制 ({} ms)，将投递至异步线程池执行", node.name(), timeout);
                    java.util.concurrent.Future<?> future = WORKFLOW_EXECUTOR.submit(() -> {
                        try {
                            node.execute(context);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
                    
                    try {
                        future.get(timeout, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (java.util.concurrent.TimeoutException e) {
                        future.cancel(true); // 发送中断信号，尝试打断阻塞操作
                        throw new java.util.concurrent.TimeoutException(String.format("执行超时熔断 (配置阈值: %d ms)", timeout));
                    } catch (java.util.concurrent.ExecutionException e) {
                        // 提取出实际抛出的业务异常
                        Throwable cause = e.getCause();
                        if (cause instanceof RuntimeException && cause.getCause() != null) {
                            cause = cause.getCause(); // 拆解上面封装的 RuntimeException
                        }
                        if (cause instanceof Exception) {
                            throw (Exception) cause;
                        } else {
                            throw new RuntimeException(cause);
                        }
                    }
                } else {
                    // 引擎负责调度，业务节点只需专注逻辑（当前线程同步执行）
                    node.execute(context);
                }

                // 如果节点执行完毕且没有引发中断，则将其标记为已完成
                if (context.canContinue()) {
                    context.getExecutedNodes().add(node.name());
                }
            } catch (Exception e) {
                log.error("工作流执行异常 [异常节点: {}]: ", node.name(), e);
                context.markFailed(node.name(), "系统异常: " + e.getMessage());
                break;
            }
        }

        // 结算最终状态
        if (context.getStatus() == WorkflowStatus.RUNNING) {
            context.put("completed", true);
            context.markSuccess();
        }

        if (context.getStatus() != WorkflowStatus.SUCCESS) {
            log.info("工作流引擎执行结束。最终状态: {}, 异常节点: [{}], 提示信息: {}",
                    context.getStatus(), context.getErrorNode(), context.getMessage());
        } else {
            log.info("工作流引擎执行结束。最终状态: SUCCESS");
        }

        return context;
    }

}
