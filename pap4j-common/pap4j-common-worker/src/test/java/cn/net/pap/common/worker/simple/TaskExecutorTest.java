package cn.net.pap.common.worker.simple;

import cn.net.pap.common.worker.executor.Task;
import cn.net.pap.common.worker.executor.TaskExecutor;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class TaskExecutorTest {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutorTest.class);

    @Test
    public void test1() throws Exception {
        TaskExecutor taskExecute = new TaskExecutor(5, 10, 100);

        for (int i = 0; i < 10; i++) {
            Task task = new Task();
            task.setId(UUID.randomUUID().toString());
            task.setProcessingTime(2000);
            taskExecute.submit(task);
        }

        int monitorSeconds = 8;
        long endTime = System.currentTimeMillis() + monitorSeconds * 1000L;
        while (System.currentTimeMillis() < endTime) {
            log.info("队列: {} 活跃线程: {}", taskExecute.getQueueSize(), taskExecute.getActiveCount());
            Thread.sleep(1000);
        }

        log.info("监控结束，关闭任务执行器...");
        taskExecute.shutdown();
        log.info("程序退出");
    }

}
