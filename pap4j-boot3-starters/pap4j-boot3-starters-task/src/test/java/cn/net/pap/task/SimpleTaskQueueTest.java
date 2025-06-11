package cn.net.pap.task;

import cn.net.pap.task.dto.SimpleTaskQueueDTO;
import cn.net.pap.task.queue.SimpleTaskQueue;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SimpleTaskQueueTest {

    private static final Logger log = LoggerFactory.getLogger(SimpleTaskQueueTest.class);

    @Test
    public void testSimpleTaskQueue() throws InterruptedException {
        SimpleTaskQueue queue = SimpleTaskQueue.getInstance();

        Thread consumerThread = queue.startConsumer();

        queue.addTask(new SimpleTaskQueueDTO("1", "task 1"));
        queue.addTask(new SimpleTaskQueueDTO("2", "task 2"));
        queue.addTask(new SimpleTaskQueueDTO("3", "task 3"));
        queue.addTask(new SimpleTaskQueueDTO("4", "task 4"));

        // 稍等线程完全退出
        consumerThread.join(500);

        List<SimpleTaskQueueDTO> simpleTaskQueueDTOS = queue.stopConsumerAndReturnUnProcessed();
        System.out.println(simpleTaskQueueDTOS);
    }

    @Test
    void testInterruptedException() throws InterruptedException {
        SimpleTaskQueue queue = SimpleTaskQueue.getInstance();
        SimpleTaskQueueDTO task = new SimpleTaskQueueDTO("1", "This should not run");
        queue.addTask(task);

        // 启动消费者线程
        Thread consumerThread = queue.startConsumer();

        // 立即中断，任务还没来得及执行
        List<SimpleTaskQueueDTO> remainingTasks = queue.stopConsumerAndReturnUnProcessed();

        // 稍等线程完全退出
        consumerThread.join(500);

        // 验证任务确实没有被执行
        assertEquals(1, remainingTasks.size(), "There should be 1 unprocessed task.");
        assertTrue(remainingTasks.contains(task), "The unprocessed task should be the one we submitted.");
        if (!remainingTasks.isEmpty()) {
            log.warn("There are {} unprocessed tasks after interruption.", remainingTasks.size());
            for (SimpleTaskQueueDTO remainingTask : remainingTasks) {
                log.warn("Unprocessed task: {}", remainingTask.toString());
            }
        }

    }

}
