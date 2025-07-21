package cn.net.pap.example.async.controller;

import cn.net.pap.example.async.config.ContextHolder;
import cn.net.pap.example.async.service.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@RestController
public class AsyncController {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @GetMapping(value = "/direct", produces = "application/json;charset=UTF-8")
    public String direct(@RequestParam(value = "index", required = false) String index) throws Exception {
        System.out.println(Thread.currentThread().getId() + " : " + System.currentTimeMillis());
        if(StringUtils.isEmpty(index)) {
            Thread.sleep((long)(Math.random() * 10000));
        } else {
            Thread.sleep(Long.parseLong(index));
        }
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

    @GetMapping("/async-data")
    public WebAsyncTask<String> getAsyncData() {
        Callable<String> callable = () -> {
            long result = 0;
            for (long i = 0; i < 1000000000L; i++) {
                result += i;
            }
            return "CPU密集型任务完成，结果: " + result;
        };

        return new WebAsyncTask<>(3000l, taskExecutor, callable);
    }

    @GetMapping("/async-with-timeout")
    public WebAsyncTask<String> getAsyncWithTimeout() {
        Callable<String> callable = () -> {
            try {
                Thread.sleep(4000);
                System.out.println("Should not reach here");
                return "Should not reach here";
            } catch (InterruptedException e) {
                System.out.println("task interrupted!");
                throw e;
            }
        };

        WebAsyncTask<String> task = new WebAsyncTask<>(2000l, callable);
        task.onTimeout(() -> "Timeout occurred");
        task.onError(() -> {
            System.out.println("task onError");
            return "Task onError";
        });

        return task;
    }

}
