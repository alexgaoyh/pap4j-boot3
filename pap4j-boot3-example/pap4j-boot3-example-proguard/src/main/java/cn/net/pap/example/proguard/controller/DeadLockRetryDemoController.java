package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import cn.net.pap.example.proguard.service.IDeadlockRetryDemoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/retry")
public class DeadLockRetryDemoController {

    private static final Logger log = LoggerFactory.getLogger(DeadLockRetryDemoController.class);

    private final IDeadlockRetryDemoService deadlockRetryDemoService;

    private final IAutoIncrePreKeyService autoIncrePreKeyService;

    public DeadLockRetryDemoController(IDeadlockRetryDemoService deadlockRetryDemoService, IAutoIncrePreKeyService autoIncrePreKeyService) {
        this.deadlockRetryDemoService = deadlockRetryDemoService;
        this.autoIncrePreKeyService = autoIncrePreKeyService;
    }

    @GetMapping("/insert")
    public String insert() throws InterruptedException {
        AutoIncrePreKey autoIncrePreKey1 = new AutoIncrePreKey();
        autoIncrePreKey1.setName("autoIncrePreKey1");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey1);

        AutoIncrePreKey autoIncrePreKey2 = new AutoIncrePreKey();
        autoIncrePreKey2.setName("autoIncrePreKey2");
        autoIncrePreKeyService.saveAndFlush(autoIncrePreKey2);

        return "done";
    }

    /**
     * h2 的数据库，可能不能重现到这个死锁，但是写法思路是相同的
     * 可以将 updateTwoRowsOrderly 这个函数内部的某一个sql的表名改为不存在的表名，然后是会出发异常到 recover 的。
     * @return
     * @throws InterruptedException
     */
    @GetMapping("/test")
    public String test() {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                2, 2, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                r -> new Thread(r, "deadlock-test-thread")
        );
        try {
            Future<?> future1 = executor.submit(() -> deadlockRetryDemoService.updateTwoRowsOrderly(1L, 2L));
            Future<?> future2 = executor.submit(() -> deadlockRetryDemoService.updateTwoRowsOrderly(2L, 1L));

            future1.get();
            future2.get();
        } catch (Exception e) {
            // 因为上面死锁的异常是在 deadlock-test-thread 抛出来的，所以如果要捕获，应该使用如上的代码，原代码是： executor.execute(runnable); 无法捕获。
            log.error("Deadlock test exception", e);
        } finally {
            // 无论前面是正常结束，还是抛出任何异常，都会进入 finally 确保线程池关闭
            try {
                log.info("正在关闭线程池1...");
                executor.shutdown(); // 拒绝新任务，等待现有任务执行完
                // 等待任务结束，最多等 30 秒
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池未能在规定时间内关闭，强制关闭...");
                    executor.shutdownNow(); // 超时强制中断
                }
                log.info("正在关闭线程池2...");
            } catch (InterruptedException e) {
                log.error("等待线程池关闭时被中断", e);
                executor.shutdownNow(); // 捕获中断异常，再次强制关闭
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
        }

        return "done";
    }

}
