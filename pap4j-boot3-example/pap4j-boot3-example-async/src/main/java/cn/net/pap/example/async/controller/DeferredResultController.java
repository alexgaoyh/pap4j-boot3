package cn.net.pap.example.async.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.CompletableFuture;

/**
 * DeferredResult
 */
@RestController
public class DeferredResultController {

    private static final Logger log = LoggerFactory.getLogger(DeferredResultController.class);

    /**
     * 简单的异步任务接口
     *
     * @param processTime 模拟处理时间(秒)
     * @param timeout     超时时间(秒)
     */
    @GetMapping("/deferred-result-simple1")
    public DeferredResult<String> deferredResultSimple1(@RequestParam(defaultValue = "10") int processTime, @RequestParam(defaultValue = "15") int timeout) {

        // 创建 DeferredResult，设置超时时间（毫秒）
        long timeoutMs = timeout * 1000L;
        DeferredResult<String> deferredResult = new DeferredResult<>(timeoutMs);

        // 设置超时回调
        deferredResult.onTimeout(() -> {
            log.warn("请求超时，已释放资源");
            deferredResult.setResult("请求超时，请重试");
        });

        // 设置完成回调（用于资源清理）
        deferredResult.onCompletion(() -> {
            log.info("异步任务完成，连接已关闭");
        });

        // 模拟异步执行耗时任务
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始执行耗时任务，预计需要 {} 秒", processTime);

                // 模拟长时间处理
                for (int i = 1; i <= processTime; i++) {
                    // 检查是否已超时（重要！）
                    if (deferredResult.isSetOrExpired()) {
                        log.info("检测到任务已超时或被取消，停止执行");
                        return;
                    }

                    log.info("任务进度: {}/{}", i, processTime);
                    Thread.sleep(1000); // 模拟1秒工作
                }

                // 任务完成，设置结果
                if (!deferredResult.isSetOrExpired()) {
                    deferredResult.setResult("任务成功完成，耗时 " + processTime + " 秒");
                }

            } catch (InterruptedException e) {
                log.warn("任务被中断");
                if (!deferredResult.isSetOrExpired()) {
                    deferredResult.setErrorResult("任务被中断");
                }
            } catch (Exception e) {
                log.error("任务执行失败: {}", e.getMessage());
                if (!deferredResult.isSetOrExpired()) {
                    deferredResult.setErrorResult("任务执行失败: " + e.getMessage());
                }
            }
        });
        log.info("请求已提交，执行中");

        return deferredResult;
    }

}
