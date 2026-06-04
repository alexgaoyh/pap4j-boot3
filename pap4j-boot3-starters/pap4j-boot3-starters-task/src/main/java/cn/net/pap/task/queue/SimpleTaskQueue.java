package cn.net.pap.task.queue;

import cn.net.pap.task.dto.SimpleTaskQueueDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleTaskQueue {

    private static final Logger log = LoggerFactory.getLogger(SimpleTaskQueue.class);

    private static final SimpleTaskQueue INSTANCE = new SimpleTaskQueue();

    // 限制队列长度
    private final BlockingQueue<SimpleTaskQueueDTO> queue = new LinkedBlockingQueue<SimpleTaskQueueDTO>(3);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private ExecutorService executorService;

    private SimpleTaskQueue() {}

    public static SimpleTaskQueue getInstance() {
        return INSTANCE;
    }

    /**
     * 因为限制了队列长度，所以这里增加一下返回值。
     * @param task
     * @return
     */
    public boolean addTask(SimpleTaskQueueDTO task) {
        return queue.offer(task);
    }

    public synchronized void startConsumer() {
        if (executorService != null && !executorService.isShutdown()) {
            return;
        }

        running.set(true);
        executorService = new ThreadPoolExecutor(
                1, 1,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(1000),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "SimpleTaskConsumer-" + counter.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executorService.execute(() -> {
            while (running.get()) {
                try {
                    // take 的时候，有可能会抛出 InterruptedException
                    // 要模拟中断异常，就要让线程在空队列里等待，然后再中断它
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
        });
    }

    public List<SimpleTaskQueueDTO> stopConsumerAndReturnUnProcessed() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("ExecutorService did not terminate in time");
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for ExecutorService termination", e);
                Thread.currentThread().interrupt();
            }
        }
        return drainUnprocessedTasks();
    }

    public boolean isRunning() {
        return running.get();
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
