package cn.net.pap.example.async.config;

import org.springframework.core.task.TaskDecorator;

/**
 * 异步任务上下文装饰器
 * <p>
 * 作用：在 Spring 线程池（ThreadPoolTaskExecutor）执行异步任务时，实现主线程（父线程）到异步线程（子线程）的 ContextHolder (ThreadLocal) 数据安全传递。
 */
public class ContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String contextValue = ContextHolder.get();
        return () -> {
            try {
                ContextHolder.set(contextValue);
                runnable.run();
            } finally {
                ContextHolder.clear();
            }
        };
    }

}

