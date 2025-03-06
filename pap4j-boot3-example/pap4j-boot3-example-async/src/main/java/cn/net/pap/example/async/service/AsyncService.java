package cn.net.pap.example.async.service;

import cn.net.pap.example.async.config.ContextHolder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class AsyncService {

    @Async("asyncExecutor")
    public CompletableFuture<String> asyncMethod() {
        try {
            String param = ContextHolder.get();
            System.out.println("执行异步方法，读取参数：" + param);
            Thread.sleep(5000);

            return CompletableFuture.completedFuture(param);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

}
