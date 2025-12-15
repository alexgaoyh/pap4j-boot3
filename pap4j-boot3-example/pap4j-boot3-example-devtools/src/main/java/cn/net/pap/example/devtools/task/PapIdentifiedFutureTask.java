package cn.net.pap.example.devtools.task;

import java.util.concurrent.FutureTask;

/**
 * 继承 FutureTask，用于在 shutdownNow() 时，安全地返回原始 PapIdentifiedTask 实例。
 */
public class PapIdentifiedFutureTask<T> extends FutureTask<T> {

    private final PapIdentifiedTask originalTask;

    public PapIdentifiedFutureTask(PapIdentifiedTask runnable, T result) {
        // 调用父类构造器，传入 PapIdentifiedTask
        super(runnable, result);
        this.originalTask = runnable;
    }

    /**
     * 暴露一个公共方法，返回被包装的 PapIdentifiedTask 实例。
     */
    public PapIdentifiedTask getOriginalTask() {
        return originalTask;
    }

}