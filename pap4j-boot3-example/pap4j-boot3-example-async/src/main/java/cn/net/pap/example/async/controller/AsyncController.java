package cn.net.pap.example.async.controller;

import cn.net.pap.example.async.config.ContextHolder;
import cn.net.pap.example.async.service.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

@RestController
public class AsyncController {

    @Autowired
    private ApplicationContext applicationContext;

    @GetMapping(value = "/direct", produces = "application/json;charset=UTF-8")
    public String direct() throws Exception {
        System.out.println(System.currentTimeMillis());
        Thread.sleep((long)(Math.random() * 10000));
        return "direct";
    }

    @GetMapping(value = "/async", produces = "application/json;charset=UTF-8")
    public String async() throws Exception {
        String requestParam = "cn.net.pap.example.async";

        ContextHolder.set(requestParam);

        AsyncService asyncService = applicationContext.getBean(AsyncService.class);
        Method method = AsyncService.class.getMethod("asyncMethod");
        Object obj = method.invoke(asyncService);
        if(obj instanceof CompletableFuture) {
            CompletableFuture<String> future = (CompletableFuture<String>) obj;
            future.thenAccept(result -> {
                System.out.println("执行异步方法，返回参数：" + result + " ; 传递的参数：" + requestParam);
            }).exceptionally(ex -> {
                ex.printStackTrace();
                return null;
            });
        }

        return "success";
    }

}
