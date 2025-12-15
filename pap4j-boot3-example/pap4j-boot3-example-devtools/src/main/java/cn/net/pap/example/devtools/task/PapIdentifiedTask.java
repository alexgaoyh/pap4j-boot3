package cn.net.pap.example.devtools.task;

/**
 * 给任务一个标识
 */
public class PapIdentifiedTask implements Runnable {

    private final String taskId;

    private final Runnable actualTask;

    public PapIdentifiedTask(String taskId, Runnable actualTask) {
        this.taskId = taskId;
        this.actualTask = actualTask;
    }

    @Override
    public void run() {
        System.out.println("Executing Task ID: " + taskId);
        actualTask.run();
    }

    public String getTaskId() {
        return taskId;
    }
}