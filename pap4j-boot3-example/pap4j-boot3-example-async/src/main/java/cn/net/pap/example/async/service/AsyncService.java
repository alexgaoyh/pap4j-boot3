package cn.net.pap.example.async.service;

import cn.net.pap.example.async.config.ContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AsyncService {

    private static final Logger log = LoggerFactory.getLogger(AsyncService.class);

    /**
     * 异步执行方法（携带 ThreadLocal 上下文）。
     *
     * @return 完成的 CompletableFuture
     */
    @Async("asyncExecutor")
    public CompletableFuture<String> asyncMethod() {
        try {
            String param = ContextHolder.get();
            log.info("执行异步方法，读取参数：{}", param);

            Thread.sleep(5000);
            return CompletableFuture.completedFuture(param.toUpperCase());
        } catch (InterruptedException e) {
            log.error("asyncMethod interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    /**
     * 模拟耗时 1 秒并转大写。
     *
     * @param param 原始字符串
     * @return 大写结果
     */
    public String method1(String param) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("method1 interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return param.toUpperCase();
    }

    /**
     * 模拟耗时 2 秒并转小写。
     *
     * @param param 原始字符串
     * @return 小写结果
     */
    public String method2(String param) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("method2 interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return param.toLowerCase();
    }

    /**
     * 模拟耗时 3 秒并追加时间戳。
     *
     * @param param 原始字符串
     * @return 拼接结果
     */
    public String method3(String param) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            log.error("method3 interrupted", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return param + ":" + System.currentTimeMillis();
    }


}
