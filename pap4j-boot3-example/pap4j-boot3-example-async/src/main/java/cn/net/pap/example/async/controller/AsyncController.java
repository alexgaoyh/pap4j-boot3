package cn.net.pap.example.async.controller;

import cn.net.pap.example.async.config.ContextHolder;
import cn.net.pap.example.async.service.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

@RestController
public class AsyncController {

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping(value = "/async", produces = "application/json;charset=UTF-8")
    public String async() throws Exception {

        ContextHolder.set("cn.net.pap.example.async");

        // 2. 反射调用异步方法
        AsyncService asyncService = applicationContext.getBean(AsyncService.class);
        Method method = AsyncService.class.getMethod("asyncMethod");
        Object obj = method.invoke(asyncService);
        if(obj instanceof Future) {
            CompletableFuture<String> future = (CompletableFuture<String>) obj;
            future.thenAccept(result -> {
                System.out.println(result);
            }).exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
        }

        return "success";
    }

}
