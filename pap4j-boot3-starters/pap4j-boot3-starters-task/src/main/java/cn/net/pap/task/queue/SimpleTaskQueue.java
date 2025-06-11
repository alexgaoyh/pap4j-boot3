package cn.net.pap.task.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SimpleTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(SimpleTaskQueue.class);

    private static final SimpleTaskQueue INSTANCE = new SimpleTaskQueue();

    private final BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    private SimpleTaskQueue() {}

    public static SimpleTaskQueue getInstance() {
        return INSTANCE;
    }

    public void addTask(Runnable task) {
        queue.offer(task);
    }

    public void startConsumer() {
        new Thread(() -> {
            while (true) {
                try {
                    Runnable task = queue.take();
                    try {
                        task.run();
                    } catch (Exception e) {
                        // todo 比如加到 死信 队列， 或者重试，或者其他
                        log.error("consumer exception : {}", e.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

}
