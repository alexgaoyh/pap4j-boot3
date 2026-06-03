package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.simple.dto.SimpleTaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 主进程：负责管理任务队列和工作进程
 */
public class SimpleMaster {

    private static final Logger log = LoggerFactory.getLogger(SimpleMaster.class);

    private BlockingQueue<SimpleTaskDTO> taskQueue = new LinkedBlockingQueue<>();

    private List<SimpleWorker> workers = new ArrayList<>();

    private ExecutorService executorService;

    private volatile boolean running = true;

    public SimpleMaster(int workerCount) {
        // 使用 ThreadPoolExecutor 管理工作进程，符合并发与线程规范
        this.executorService = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    int id = workers.size(); // 这里仅作为演示，实际 id 可能需要更严谨的计数
                    Thread t = new Thread(r, "SimpleWorker-" + id);
                    t.setDaemon(true);
                    return t;
                }
        );

        // 创建工作进程
        for (int i = 0; i < workerCount; i++) {
            SimpleWorker worker = new SimpleWorker(i, taskQueue);
            workers.add(worker);
            executorService.execute(worker);
        }
    }

    /**
     * 提交任务到队列
     *
     * @param task
     */
    public void submitTask(SimpleTaskDTO task) {
        try {
            taskQueue.put(task);
            log.info("Master: 提交任务 {}", task.getId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 停止所有工作进程
     */
    public void shutdown() {
        running = false;
        for (SimpleWorker worker : workers) {
            worker.stop();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Master: 已停止所有工作进程");
    }

    /**
     * 查看任务队列状态
     */
    public void showStatus() {
        log.info("=== 系统状态 ===");
        log.info("待处理任务数: {}", taskQueue.size());
        log.info("工作进程数: {}", workers.size());
        log.info("==============");
    }

}
