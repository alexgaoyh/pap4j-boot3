package cn.net.pap.task.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

public class RateLimitedUtil {

    private static final Logger log = LoggerFactory.getLogger(RateLimitedUtil.class);

    private RateLimitedUtil() {
    }

    // 默认最大并发数
    private static final Semaphore semaphore = new Semaphore(10);

    /**
     * 执行带限流的异步任务
     *
     * @param task 要执行的异步任务
     * @param <T>  返回类型
     * @return 限流后的Mono结果
     */
    public static <T> Mono<T> executeWithRateLimit(Supplier<Mono<T>> task) {
        return Mono.fromCallable(() -> {
                    if (!semaphore.tryAcquire()) {
                        throw new RuntimeException("系统繁忙，请稍后重试");
                    }
                    return true;
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMap(
                        ignored -> task.get().doOnSuccess(result -> {
                            System.out.println("任务执行成功");
                            semaphore.release();
                        }).doOnError(error -> {
                            System.out.println("任务执行失败: " + error.getMessage());
                            semaphore.release();
                        })
                )
                .onErrorResume(e -> {
                    System.out.println("限流任务执行失败: " + e.getMessage());
                    // 只有在成功获取许可但后续出错时才释放
                    if (!e.getMessage().equals("系统繁忙，请稍后重试")) {
                        semaphore.release();
                    }
                    return Mono.error(e);
                });
    }

    /**
     * 获取当前限流状态
     */
    public static String getRateLimitStatus() {
        return String.format("可用许可:%d, 等待队列:%d", semaphore.availablePermits(), semaphore.getQueueLength());
    }

}
