package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.simple.dto.SimpleTaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 工作进程：从队列取任务并执行
 */
public class SimpleWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SimpleWorker.class);

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

        log.info("Worker-{}: 启动", id);

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

        log.info("Worker-{}: 停止，共处理 {} 个任务", id, processedCount);
    }

    private void processTask(SimpleTaskDTO task) {
        log.info("Worker-{}: 开始处理任务 {}", id, task.getId());

        // 模拟任务处理时间
        try {
            Thread.sleep(task.getProcessingTime());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Worker-{}: 完成任务 {}", id, task.getId());
    }

    public void stop() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }
}
