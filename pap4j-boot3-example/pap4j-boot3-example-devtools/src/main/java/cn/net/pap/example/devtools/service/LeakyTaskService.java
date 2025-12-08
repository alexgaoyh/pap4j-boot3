package cn.net.pap.example.devtools.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class LeakyTaskService {
    // 模拟一个单线程池
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @PostConstruct
    public void init() {
        System.out.println(">>> LeakService 初始化, 线程池 Hash: " + executorService.hashCode());

        executorService.submit(() -> {
            try {
                while (true) {
                    // 模拟耗时循环任务
                    System.out.println(">>> 线程 [" + Thread.currentThread().getName() + "] 正在运行, 线程池 Hash: " + executorService.hashCode());
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                System.out.println(">>> 线程被中断了");
            }
        });
    }

}