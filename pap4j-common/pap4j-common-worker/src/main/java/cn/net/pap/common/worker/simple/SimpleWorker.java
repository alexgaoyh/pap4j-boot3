package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.simple.dto.SimpleTaskDTO;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 工作进程：从队列取任务并执行
 */
public class SimpleWorker implements Runnable {

    private final int id;

    private final BlockingQueue<SimpleTaskDTO> taskQueue;

    private volatile boolean running = true;

    private int processedCount = 0;

    // 保存线程引用
    private Thread workerThread;

    public SimpleWorker(int id, BlockingQueue<SimpleTaskDTO> taskQueue) {
        this.id = id;
        this.taskQueue = taskQueue;
    }

    @Override
    public void run() {
        // 保存当前线程
        workerThread = Thread.currentThread();

        System.out.println("Worker-" + id + ": 启动");

        while (running) {
            try {
                // 从队列获取任务（会阻塞直到有任务）
                SimpleTaskDTO task = taskQueue.poll(100, TimeUnit.MILLISECONDS);

                if (task != null) {
                    processTask(task);
                    processedCount++;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        System.out.println("Worker-" + id + ": 停止，共处理 " + processedCount + " 个任务");
    }

    private void processTask(SimpleTaskDTO task) {
        System.out.println("Worker-" + id + ": 开始处理任务 " + task.getId());

        // 模拟任务处理时间
        try {
            Thread.sleep(task.getProcessingTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Worker-" + id + ": 完成任务 " + task.getId());
    }

    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
