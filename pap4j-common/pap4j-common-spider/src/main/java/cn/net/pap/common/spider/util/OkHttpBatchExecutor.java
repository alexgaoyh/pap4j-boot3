package cn.net.pap.common.spider.util;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p><strong>OkHttpBatchExecutor</strong></p>
 * <p>高效、资源受控的 OkHttp 批量请求执行器。优化为针对固定 URL 发送不同 JSON Body 的场景。</p>
 */
public class OkHttpBatchExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OkHttpBatchExecutor.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final ThreadPoolExecutor executor;

    /**
     * 任务执行状态枚举
     */
    public enum Status {
        /**
         * 成功
         */
        SUCCESS,
        /**
         * 网络或业务逻辑失败
         */
        FAILED,
        /**
         * 队列已满，被执行器拒绝
         */
        REJECTED,
        /**
         * 未知状态（如 Future 中断）
         */
        UNKNOWN
    }

    /**
     * 批量结果记录 (JDK 17 Record)
     *
     * @param input  原始输入 JSON
     * @param status 执行状态
     * @param data   成功时的响应数据
     * @param error  失败时的错误信息
     */
    public record BatchResult(String input, Status status, String data, String error) {
        public static BatchResult success(String input, String data) {
            return new BatchResult(input, Status.SUCCESS, data, null);
        }

        public static BatchResult failure(String input, String error) {
            return new BatchResult(input, Status.FAILED, null, error);
        }

        public static BatchResult rejected(String input, String error) {
            return new BatchResult(input, Status.REJECTED, null, error);
        }

        public static BatchResult unknown(String input, String error) {
            return new BatchResult(input, Status.UNKNOWN, null, error);
        }
    }

    /**
     * 构造函数
     *
     * @param client        OkHttpClient 实例（建议单例）
     * @param corePoolSize  核心线程数
     * @param maxPoolSize   最大线程数
     * @param queueCapacity 任务队列容量（有界）
     */
    public OkHttpBatchExecutor(OkHttpClient client, int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.client = client;
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(1);

                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "okhttp-batch-" + counter.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 批量发送 POST 请求（JSON Body）
     *
     * @param url        请求地址
     * @param jsonBodies JSON 字符串列表
     * @return 结果列表
     */
    public List<BatchResult> executeBatch(String url, List<String> jsonBodies) {
        if (jsonBodies == null || jsonBodies.isEmpty()) {
            return Collections.emptyList();
        }

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(jsonBodies.size());
        for (String json : jsonBodies) {
            final String currentInput = json;
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Request request = new Request.Builder()
                            .url(url)
                            .post(RequestBody.create(currentInput, JSON_TYPE))
                            .build();
                    try (Response response = client.newCall(request).execute()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            return BatchResult.failure(currentInput, "HTTP " + response.code() + ": " + body);
                        }
                        return BatchResult.success(currentInput, body);
                    } catch (Exception e) {
                        log.error("HTTP 请求执行失败: {}", url, e);
                        return BatchResult.failure(currentInput, e.getMessage());
                    }
                }, executor));
            } catch (RejectedExecutionException e) {
                log.warn("任务提交被拒绝 (队列已满): {}", currentInput);
                futures.add(CompletableFuture.completedFuture(BatchResult.rejected(currentInput, "Queue full")));
            }
        }

        return futures.stream()
                .map(f -> {
                    try {
                        return f.join();
                    } catch (Exception e) {
                        return BatchResult.unknown("N/A", "Future join failed: " + e.getMessage());
                    }
                })
                .toList();
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
