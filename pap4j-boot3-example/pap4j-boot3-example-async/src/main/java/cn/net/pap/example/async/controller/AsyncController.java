package cn.net.pap.example.async.controller;

import cn.net.pap.example.async.config.ContextHolder;
import cn.net.pap.example.async.constant.AsyncConstant;
import cn.net.pap.example.async.service.AsyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.WebAsyncTask;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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

    /**
     * 异步多线程环境下的上下文传递（Context Propagation）
     * 在实际业务中，由于 Spring 的 @Async 会把任务交给全新的线程去执行，而 ThreadLocal 默认是线程隔离的，所以主线程的数据通常会“传不过去”。这段代码就是为了解决这个问题。
     * <p>
     * 演示与测试异步环境下的上下文（ThreadLocal）安全传递机制。
     * <p>
     * 执行流程：
     * 1. 【初始化】：主线程（Tomcat工作线程）接收请求，将关键参数放入 ContextHolder（基于 ThreadLocal）。
     * 2. 【触发异步】：调用被 @Async 注解标记的方法，将耗时任务提交给自定义的异步线程池。
     * 3. 【非阻塞响应】：主线程不等待异步任务完成，直接向客户端返回 "success"。
     * 4. 【安全清理】：使用 finally 块强制清空主线程的 ContextHolder。防止因 Tomcat 线程池复用导致的数据串号（下一个请求读到上一个请求的数据）以及内存泄漏（OOM）。
     *
     * @return 响应字符串 "success"
     * @throws Exception 处理过程中的通用异常
     */
    @GetMapping(value = "/async", produces = "application/json;charset=UTF-8")
    public String async() throws Exception {
        String requestParam = "cn.net.pap.example.async";

        try {
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
        } finally {
            ContextHolder.clear();
        }
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

    /**
     * 组合任务
     *
     * @return
     */
    @GetMapping("/composite-async")
    public WebAsyncTask<String> compositeAsyncTask() {
        // 第一阶段
        Callable<String> stage1 = () -> {
            Thread.sleep(1000);
            return "Stage1-Result";
        };

        // 第二阶段
        Callable<String> compositeCallable = () -> {
            String stage1Result = stage1.call();
            return stage1Result + "-compositeCallable";
        };

        WebAsyncTask<String> task = new WebAsyncTask<>(3000, compositeCallable);

        // 回调
        task.onCompletion(() -> System.out.println("Composite task completed"));

        return task;
    }

    /**
     * 启动可取消的异步任务
     *
     * @param taskId 任务ID
     * @return WebAsyncTask 异步任务
     */
    @GetMapping("/start-task")
    public WebAsyncTask<String> startTask(@RequestParam String taskId) {
        // 初始化取消标志和进度
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicInteger progress = new AtomicInteger(0);

        AsyncConstant.cancellationFlags.put(taskId, cancelled);
        AsyncConstant.taskProgress.put(taskId, progress);

        Callable<String> callable = () -> {
            try {
                // 模拟任务执行，10个步骤，每个步骤增加10%进度
                for (int i = 0; i < 10; i++) {
                    // 检查是否取消
                    if (cancelled.get()) {
                        return "Task " + taskId + " cancelled at " + progress.get() + "%";
                    }
                    // 模拟工作单元
                    Thread.sleep(1000);
                    // 更新进度
                    int newProgress = (i + 1) * 10;
                    progress.set(newProgress);
                    System.out.println(taskId + " - Progress: " + newProgress + "%");
                }
                return "Task " + taskId + " completed successfully";
            } finally {
                // 清理资源
                AsyncConstant.cancellationFlags.remove(taskId);
                AsyncConstant.taskProgress.remove(taskId);
            }
        };

        // 创建异步任务，设置5秒超时
        WebAsyncTask<String> task = new WebAsyncTask<>(105000, callable);
        // 超时处理
        task.onTimeout(() -> {
            cancelled.set(true);
            return "Task " + taskId + " timed out at " + progress.get() + "%";
        });
        return task;
    }

    /**
     * 取消正在执行的任务
     *
     * @param taskId 任务ID
     * @return 取消结果
     */
    @GetMapping("/cancel-task")
    public ResponseEntity<String> cancelTask(@RequestParam String taskId) {
        AtomicBoolean flag = AsyncConstant.cancellationFlags.get(taskId);
        if (flag != null) {
            flag.set(true);
            int progress = AsyncConstant.taskProgress.getOrDefault(taskId, new AtomicInteger(0)).get();
            return ResponseEntity.ok("Task " + taskId + " cancellation requested. Progress was: " + progress + "%");
        }
        return ResponseEntity.ok("No Task");
    }

    /**
     * 查询任务进度
     *
     * @param taskId 任务ID
     * @return 当前进度(0 - 100)
     */
    @GetMapping("/progress")
    public ResponseEntity<Integer> getProgress(@RequestParam String taskId) {
        AtomicInteger progress = AsyncConstant.taskProgress.get(taskId);
        if (progress != null) {
            return ResponseEntity.ok(progress.get());
        }
        return ResponseEntity.ok(-1);
    }

    /**
     * 查询所有任务状态
     *
     * @return 所有活跃任务的状态
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAllTaskStatus() {
        Map<String, Object> statusMap = new HashMap<>();

        AsyncConstant.cancellationFlags.forEach((taskId, flag) -> {
            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("cancelled", flag.get());
            taskInfo.put("progress", AsyncConstant.taskProgress.getOrDefault(taskId, new AtomicInteger(0)).get());
            statusMap.put(taskId, taskInfo);
        });

        return ResponseEntity.ok(statusMap);
    }


}
