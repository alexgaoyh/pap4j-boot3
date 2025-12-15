package cn.net.pap.example.devtools.executor;

import cn.net.pap.example.devtools.task.PapIdentifiedFutureTask;
import cn.net.pap.example.devtools.task.PapIdentifiedTask;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PapIdentifiedThreadPoolExecutor extends ThreadPoolExecutor {

    // 假设使用 ThreadPoolExecutor 的完整构造器
    public PapIdentifiedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * 覆盖此方法，当提交的是 PapIdentifiedTask 时，使用我们自定义的 IdentifiedFutureTask 包装器。
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        if (runnable instanceof PapIdentifiedTask) {
            // 返回自定义的 FutureTask
            return new PapIdentifiedFutureTask<>((PapIdentifiedTask) runnable, value);
        }
        // 对于其他类型的 Runnable，使用默认的 FutureTask
        return super.newTaskFor(runnable, value);
    }

    /**
     * 唯一允许的 submit 方法
     */
    public Future<?> submit(PapIdentifiedTask task) {
        if (task == null) {
            throw new NullPointerException("PapIdentifiedTask must not be null");
        }
        return super.submit(task);
    }

    /**
     * 禁用父类的 submit(Runnable)
     */
    @Override
    @Deprecated
    public Future<?> submit(Runnable task) {
        throw new UnsupportedOperationException("Only PapIdentifiedTask is allowed. Please wrap your task.");
    }

    /**
     * 禁用 submit(Callable)
     */
    @Override
    @Deprecated
    public <T> Future<T> submit(Callable<T> task) {
        throw new UnsupportedOperationException("Callable is not supported. Use PapIdentifiedTask instead.");
    }

    // 如果需要支持 Callable，请覆盖 newTaskFor(Callable<T> callable)
}