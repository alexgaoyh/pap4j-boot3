package cn.net.pap.example.devtools.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class LeakyTaskService {
    // 模拟一个单线程池
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * 这里的代码，没有对线程池的关闭，会出现泄露情况
     * 旧的容器虽然已销毁，但由于自定义线程池未显式关闭，其产生的非守护线程会持续运行并持有旧类加载器的引用，导致旧的类对象无法被回收。
     *
     * 操作示例： 打出来war之后，使用 tomcat->manager 进行部署和卸载，即便是卸载了，下面的这个打印还是存在，并且还会有此次新增的线程池，会有多个在跑，是不对的。
     */
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

    // 热部署重启时，Spring 会调用此销毁方法
//    @PreDestroy
//    public void shutdown() {
//        System.out.println(">>> 正在关闭线程池...");
//        executorService.shutdownNow(); // 发送 interrupt 信号
//        try {
//            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
//                System.err.println(">>> 线程池未能在规定时间内关闭");
//            }
//        } catch (InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
//    }

}