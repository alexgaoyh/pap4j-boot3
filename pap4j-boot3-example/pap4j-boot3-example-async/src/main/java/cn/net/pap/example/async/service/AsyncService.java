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

    @Async("asyncExecutor")
    public CompletableFuture<String> asyncMethod() {
        try {
            String param = ContextHolder.get();
            log.info("执行异步方法，读取参数：{}", param);

            Thread.sleep(5000);
            return CompletableFuture.completedFuture(param.toUpperCase());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

    }

    public String method1(String param) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return param.toUpperCase();
    }

    public String method2(String param) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return param.toLowerCase();
    }

    public String method3(String param) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return param + ":" + System.currentTimeMillis();
    }


}
