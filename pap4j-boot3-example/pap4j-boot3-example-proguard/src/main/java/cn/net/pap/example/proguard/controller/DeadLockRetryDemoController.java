package cn.net.pap.example.proguard.controller;

import cn.net.pap.example.proguard.entity.AutoIncrePreKey;
import cn.net.pap.example.proguard.service.IAutoIncrePreKeyService;
import cn.net.pap.example.proguard.service.IDeadlockRetryDemoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/retry")
@Tag(name = "死锁重试与恢复测试接口", description = "演示通过 Spring Retry 实现的数据库死锁重试与兜底恢复机制")
public class DeadLockRetryDemoController {

    private static final Logger log = LoggerFactory.getLogger(DeadLockRetryDemoController.class);

    private final IDeadlockRetryDemoService deadlockRetryDemoService;

    private final IAutoIncrePreKeyService autoIncrePreKeyService;

    private final ThreadPoolExecutor deadlockExecutor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(10),
            new CustomizableThreadFactory("deadlock-test-thread-"),
            new ThreadPoolExecutor.AbortPolicy()
    );

    public DeadLockRetryDemoController(IDeadlockRetryDemoService deadlockRetryDemoService, IAutoIncrePreKeyService autoIncrePreKeyService) {
        this.deadlockRetryDemoService = deadlockRetryDemoService;
        this.autoIncrePreKeyService = autoIncrePreKeyService;
    }

    @PreDestroy
    public void shutdownExecutor() {
        log.info("Shutting down deadlock test executor...");
        deadlockExecutor.shutdown();
        try {
            if (!deadlockExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                deadlockExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            deadlockExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Operation(summary = "初始化测试数据", description = "往数据库插入两条自增主键数据作为死锁测试的基础数据。")
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
    @Operation(summary = "触发并测试死锁重试逻辑", description = "使用线程池并发以相反的顺序更新两条数据库记录，以此产生死锁，并测试 Retry 重试。")
    @GetMapping("/test")
    public String test() {
        try {
            Future<?> future1 = deadlockExecutor.submit(() -> deadlockRetryDemoService.updateTwoRowsOrderly(1L, 2L));
            Future<?> future2 = deadlockExecutor.submit(() -> deadlockRetryDemoService.updateTwoRowsOrderly(2L, 1L));

            future1.get();
            future2.get();
        } catch (Exception e) {
            log.error("Deadlock test exception", e);
        }
        return "done";
    }

    @Operation(summary = "模拟 400 错误响应 (4xx)", description = "显式返回 400 Bad Request 状态码及错误信息，用于测试日志过滤器的 4xx 快照录制功能。")
    @GetMapping("/error400")
    public ResponseEntity<String> error400() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("{\"error\": \"Invalid parameter\", \"code\": \"BAD_REQUEST\"}");
    }

    @Operation(summary = "模拟 500 系统异常 (5xx)", description = "抛出运行时异常，用于测试日志过滤器捕获未处理异常并录制 500 堆栈快照的功能。")
    @GetMapping("/error500")
    public String error500() {
        throw new RuntimeException("Simulated 500 internal server error for deadlock test");
    }

}
