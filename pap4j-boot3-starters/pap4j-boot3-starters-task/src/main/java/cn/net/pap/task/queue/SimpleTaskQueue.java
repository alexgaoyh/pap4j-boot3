package cn.net.pap.task.queue;

import cn.net.pap.task.dto.SimpleTaskQueueDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class SimpleTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(SimpleTaskQueue.class);

    private static final SimpleTaskQueue INSTANCE = new SimpleTaskQueue();

    private final BlockingQueue<SimpleTaskQueueDTO> queue = new LinkedBlockingQueue<SimpleTaskQueueDTO>();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread consumerThread;

    private SimpleTaskQueue() {}

    public static SimpleTaskQueue getInstance() {
        return INSTANCE;
    }

    public void addTask(SimpleTaskQueueDTO task) {
        queue.offer(task);
    }

    public Thread startConsumer() {
        if (consumerThread != null && consumerThread.isAlive()) {
            return consumerThread;
        }

        running.set(true);
        consumerThread = new Thread(() -> {
            while (running.get()) {
                try {
                    SimpleTaskQueueDTO task = queue.take();
                    try {
                        log.info("Consumed task: {}", task.toString());
                    } catch (Exception e) {
                        log.error("Consumer Task execution exception: {}", e.getMessage(), e);
                    }
                } catch (InterruptedException e) {
                    log.warn("Consumer thread was interrupted. Shutting down...");
                    running.set(false);
                    Thread.currentThread().interrupt();
                }
            }
        }, "SimpleTaskConsumer");

        consumerThread.start();
        return consumerThread;
    }

    public List<SimpleTaskQueueDTO> stopConsumerAndReturnUnProcessed() {
        running.set(false);
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
        return drainUnprocessedTasks();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Thread getConsumerThread() {
        return consumerThread;
    }

    public int getPendingTaskCount() {
        return queue.size();
    }

    public List<SimpleTaskQueueDTO> drainUnprocessedTasks() {
        List<SimpleTaskQueueDTO> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        return remaining;
    }
}
