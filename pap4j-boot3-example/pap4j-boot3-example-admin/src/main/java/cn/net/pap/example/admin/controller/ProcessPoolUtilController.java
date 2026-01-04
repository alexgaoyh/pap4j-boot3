package cn.net.pap.example.admin.controller;

import cn.net.pap.example.admin.dto.ProcessResult;
import cn.net.pap.example.admin.util.ProcessPoolUtil;
import cn.net.pap.example.admin.util.ProcessPoolUtilExample;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/processPoolUtil")
public class ProcessPoolUtilController {

    private final ExecutorService executor = Executors.newFixedThreadPool(5); // 最多同时 5 个子进程

    /**
     * 最简单的“任务表”
     */
    private final ConcurrentHashMap<String, ProcessResult> results = new ConcurrentHashMap<>();


    /**
     * Controller 销毁前关闭线程池，释放资源
     */
    @PreDestroy
    public void shutdownExecutor() {
        System.out.println("[ProcessController] Shutting down executor...");
        executor.shutdown();
    }

    @RequestMapping("/java")
    public String runJavaFuture() {

        String mainClass = ProcessPoolUtilExample.class.getName();

        String taskId = UUID.randomUUID().toString();
        ProcessResult result = new ProcessResult();
        results.put(taskId, result);

        executor.execute(() -> {
            ProcessResult r = ProcessPoolUtil.runJavaClass(
                    mainClass, null, 0
            );
            result.exitCode = r.getExitCode();
            result.output = r.getOutput();
            result.finished = r.isFinished();
        });

        return taskId;
    }

}
