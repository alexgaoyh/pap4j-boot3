package cn.net.pap.task;

import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class InterruptedExceptionTest {

    // @Test
    public void exceptionTest1() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(1);

        Runnable task = () -> {
            System.out.println("任务开始");
            try {
                // 模拟长时间运行（但没有处理中断）
                while (true) {
                    // Thread.sleep 被中断会抛 InterruptedException，但这里被 catch 后忽略了
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // 错误示范：捕获后没有处理，导致线程继续运行
                        System.out.println("收到中断信号，但忽略...");
                        // compare : need call Thread.currentThread().interrupt(); // 重新设置中断标志位
                        // compare : need call break; // 跳出循环
                    }
                }
            } finally {
                System.out.println("任务结束");
            }
        };

        executor.submit(task);

        Thread.sleep(2000); // 等待任务开始执行

        System.out.println("尝试关闭线程池...");
        executor.shutdownNow(); // 试图强制终止线程

        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            System.out.println("线程池没有成功关闭，任务可能没有响应中断。");
        } else {
            System.out.println("线程池关闭成功");
        }
    }

}
