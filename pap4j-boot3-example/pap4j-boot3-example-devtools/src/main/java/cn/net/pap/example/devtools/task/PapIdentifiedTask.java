package cn.net.pap.example.devtools.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 给任务一个标识
 */
public class PapIdentifiedTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(PapIdentifiedTask.class);

    private final String taskId;

    private final Runnable actualTask;

    public PapIdentifiedTask(String taskId, Runnable actualTask) {
        this.taskId = taskId;
        this.actualTask = actualTask;
    }

    @Override
    public void run() {
        log.info("Executing Task ID: {}", taskId);
        actualTask.run();
    }

    public String getTaskId() {
        return taskId;
    }
}