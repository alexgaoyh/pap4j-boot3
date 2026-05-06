package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.simple.dto.SimpleTaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 主进程：负责管理任务队列和工作进程
 */
public class SimpleMaster {

    private static final Logger log = LoggerFactory.getLogger(SimpleMaster.class);

    private BlockingQueue<SimpleTaskDTO> taskQueue = new LinkedBlockingQueue<>();

    private List<SimpleWorker> workers = new ArrayList<>();

    private volatile boolean running = true;

    public SimpleMaster(int workerCount) {
        // 创建工作进程
        for (int i = 0; i < workerCount; i++) {
            SimpleWorker worker = new SimpleWorker(i, taskQueue);
            workers.add(worker);
            new Thread(worker, "SimpleWorker-" + i).start();
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
