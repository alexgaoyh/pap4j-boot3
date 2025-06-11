package cn.net.pap.task;

import cn.net.pap.task.queue.SimpleTaskQueue;
import org.junit.jupiter.api.Test;

public class SimpleTaskQueueTest {

    @Test
    public void testSimpleTaskQueue() {
        SimpleTaskQueue queue = SimpleTaskQueue.getInstance();

        queue.startConsumer();

        queue.addTask(() -> System.out.println("task 1"));
        queue.addTask(() -> System.out.println("task 2"));

        queue.addTask(() -> {
            System.out.println("task 3");
            throw new RuntimeException("failed task 3 exception msg");
        });

        queue.addTask(() -> System.out.println("task 4"));
    }

}
