package cn.net.pap.common.spider.util;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p><strong>OkHttpBatchExecutor</strong></p>
 * <p>高效、资源受控的 OkHttp 批量请求执行器。优化为针对固定 URL 发送不同 JSON Body 的场景。</p>
 *
 * <h3>内存风险提示（重要）</h3>
 * <p>所有请求的响应体会全量保留在内存中（作为 {@link BatchResult#data()} 字段），
 * 直到 {@link #executeBatch(String, List)} 方法返回。因此当单批输入数量极大
 * （如数万笔以上）或单个响应体较大时，可能产生较大内存压力，极端情况下可能触发 OOM。
 * 请通过 {@code maxBatchSize} 参数限制单批请求数量，并在业务层面评估响应体大小。</p>
 *
 * <h3>并发安全</h3>
 * <p>{@link #executeBatch(String, List)} 内部对每个 future 使用超时等待（默认 30 秒），
 * 因此当与 {@link #close()} 并发调用时，已在线程池排队但未启动的任务不会永久阻塞调用方，
 * 会在超时后以 {@link Status#UNKNOWN} 状态返回。结果列表长度始终与输入列表一致。</p>
 */
public class OkHttpBatchExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(OkHttpBatchExecutor.class);
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");
    private static final long DEFAULT_JOIN_TIMEOUT_MS = 30_000L;

    /**
     * 创建一个忽略 SSL 证书校验的 OkHttpClient.Builder（用于开发调试或爬虫绕过证书错误）
     *
     * @return OkHttpClient.Builder
     */
    public static OkHttpClient.Builder createUnsafeOkHttpClientBuilder() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            log.error("Failed to create unsafe OkHttpClient", e);
            throw new RuntimeException("Failed to create unsafe OkHttpClient", e);
        }
    }

    /**
     * 创建一个忽略 SSL 证书校验的 OkHttpClient，并附加指定的多个 HTTP 请求头（支持 null 或空值）
     *
     * @param headers 自定义请求头键值对 Map，可以为 null 或为空
     * @return 配置好的 OkHttpClient 实例
     */
    public static OkHttpClient createUnsafeOkHttpClient(java.util.Map<String, String> headers) {
        OkHttpClient.Builder builder = createUnsafeOkHttpClientBuilder();
        if (headers != null && !headers.isEmpty()) {
            builder.addInterceptor(chain -> {
                Request original = chain.request();
                Request.Builder requestBuilder = original.newBuilder();
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        requestBuilder.header(entry.getKey(), entry.getValue());
                    }
                }
                return chain.proceed(requestBuilder.build());
            });
        }
        return builder.build();
    }

    /**
     * 同步发送单笔 POST 请求（JSON Body）并直接返回响应字符串内容（不创建或使用线程池）
     *
     * @param client   OkHttpClient 实例
     * @param url      请求目标地址
     * @param jsonBody 请求 JSON 内容
     * @return 响应体字符串内容
     * @throws IOException 请求失败或网络异常时抛出
     */
    public static String executePost(OkHttpClient client, String url, String jsonBody) throws IOException {
        if (client == null) {
            throw new IOException("OkHttpClient is null");
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    private final OkHttpClient client;
    private final ThreadPoolExecutor executor;
    private final int maxBatchSize;
    private final long joinTimeoutMs;

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
         * 未知状态（如 Future 中断、超时）
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
     * @param client        OkHttpClient 实例（建议单例，允许为 null，但执行时会直接返回失败结果）
     * @param corePoolSize  核心线程数
     * @param maxPoolSize   最大线程数
     * @param queueCapacity 任务队列容量（有界）
     * @param maxBatchSize  单次 {@link #executeBatch(String, List)} 允许的最大输入数量。
     *                      超过时直接返回同等数量的 {@link Status#REJECTED} 结果，防止 OOM。
     *                      值 {@code <= 0} 表示不限制。
     */
    public OkHttpBatchExecutor(OkHttpClient client, int corePoolSize, int maxPoolSize, int queueCapacity, int maxBatchSize) {
        this(client, corePoolSize, maxPoolSize, queueCapacity, maxBatchSize, DEFAULT_JOIN_TIMEOUT_MS);
    }

    /**
     * 内部构造函数（包级私有，用于测试注入更短的 join 超时）
     *
     * @param joinTimeoutMs 每个 Future.get() 的超时毫秒数
     */
    OkHttpBatchExecutor(OkHttpClient client, int corePoolSize, int maxPoolSize, int queueCapacity, int maxBatchSize, long joinTimeoutMs) {
        this.client = client;
        this.maxBatchSize = maxBatchSize;
        this.joinTimeoutMs = joinTimeoutMs;
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
     * <p>
     * <b>内存说明：</b>返回的 {@link BatchResult} 列表中所有响应体数据全量驻留内存，
     * 调用方应避免单批次提交过量请求，具体参考类文档说明。
     * </p>
     *
     * @param url        请求地址
     * @param jsonBodies JSON 字符串列表
     * @return 结果列表，长度始终与 jsonBodies 一致（null 或空时返回空列表）
     */
    public List<BatchResult> executeBatch(String url, List<String> jsonBodies) {
        if (jsonBodies == null || jsonBodies.isEmpty()) {
            return Collections.emptyList();
        }

        // ── 1. maxBatchSize 保护：防止单批请求量过大导致 OOM ──
        if (maxBatchSize > 0 && jsonBodies.size() > maxBatchSize) {
            log.warn("批量请求数量 {} 超过上限 maxBatchSize={}，全部拒绝", jsonBodies.size(), maxBatchSize);
            List<BatchResult> results = new ArrayList<>(jsonBodies.size());
            for (String json : jsonBodies) {
                results.add(BatchResult.rejected(json,
                        "Batch size " + jsonBodies.size() + " exceeds max " + maxBatchSize));
            }
            return results;
        }

        // ── 2. OkHttpClient 为空保护 ──
        if (client == null) {
            log.warn("OkHttpClient 为 null，所有请求直接返回失败");
            List<BatchResult> results = new ArrayList<>(jsonBodies.size());
            for (String json : jsonBodies) {
                results.add(BatchResult.failure(json, "client null"));
            }
            return results;
        }

        // ── 3. 正常提交 ──
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
                        log.error("HTTP 请求执行失败: {}, {}", url, json, e);
                        return BatchResult.failure(currentInput, e.getMessage());
                    }
                }, executor));
            } catch (RejectedExecutionException e) {
                log.error("任务提交被拒绝 (队列已满): {}", currentInput, e);
                futures.add(CompletableFuture.completedFuture(BatchResult.rejected(currentInput, "Queue full")));
            }
        }

        // ── 4. 收集结果（带超时，防止并发 close() 导致无限阻塞） ──
        List<BatchResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<BatchResult> f = futures.get(i);
            // 防御性获取 input：理论上 futures.size() == jsonBodies.size()
            String input = i < jsonBodies.size() ? jsonBodies.get(i) : "N/A";
            try {
                results.add(f.get(joinTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                log.error("任务执行超时: {}", input, e);
                f.cancel(true); // 中断仍在执行的底层线程
                results.add(BatchResult.unknown(input,
                        "Task timed out after " + joinTimeoutMs + "ms"));
            } catch (ExecutionException e) {
                log.error("任务执行异常", e);
                Throwable cause = e.getCause();
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                results.add(BatchResult.unknown(input, msg != null ? msg : "Task execution failed"));
            } catch (CancellationException e) {
                log.error("任务被取消", e);
                results.add(BatchResult.unknown(input, "Task cancelled"));
            } catch (InterruptedException e) {
                // 恢复中断状态，调用方可通过 Thread.currentThread().isInterrupted() 感知
                log.error("线程被中断", e);
                Thread.currentThread().interrupt();
                results.add(BatchResult.unknown(input, "Thread interrupted"));
            }
        }
        return results;
    }

    /**
     * 批量发送 POST 请求，每个 URL 对应一个请求，使用同一个 JSON Body（用于针对不同 URL 批量请求）
     *
     * @param urls       请求地址列表
     * @param jsonBody   统一的 JSON 请求体内容
     * @return 结果列表，其顺序与 urls 一致，BatchResult 中的 input 保存对应的 URL
     */
    public List<BatchResult> executeBatch(List<String> urls, String jsonBody) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }

        // ── 1. maxBatchSize 保护 ──
        if (maxBatchSize > 0 && urls.size() > maxBatchSize) {
            log.warn("批量请求数量 {} 超过上限 maxBatchSize={}，全部拒绝", urls.size(), maxBatchSize);
            List<BatchResult> results = new ArrayList<>(urls.size());
            for (String url : urls) {
                results.add(BatchResult.rejected(url,
                        "Batch size " + urls.size() + " exceeds max " + maxBatchSize));
            }
            return results;
        }

        // ── 2. OkHttpClient 为空保护 ──
        if (client == null) {
            log.warn("OkHttpClient 为 null，所有请求直接返回失败");
            List<BatchResult> results = new ArrayList<>(urls.size());
            for (String url : urls) {
                results.add(BatchResult.failure(url, "client null"));
            }
            return results;
        }

        // ── 3. 正常提交 ──
        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(urls.size());
        for (String url : urls) {
            final String currentUrl = url;
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    Request request = new Request.Builder()
                            .url(currentUrl)
                            .post(RequestBody.create(jsonBody, JSON_TYPE))
                            .build();
                    try (Response response = client.newCall(request).execute()) {
                        String body = response.body() != null ? response.body().string() : "";
                        if (!response.isSuccessful()) {
                            return BatchResult.failure(currentUrl, "HTTP " + response.code() + ": " + body);
                        }
                        return BatchResult.success(currentUrl, body);
                    } catch (Exception e) {
                        log.error("HTTP 请求执行失败: {}, {}", currentUrl, jsonBody, e);
                        return BatchResult.failure(currentUrl, e.getMessage());
                    }
                }, executor));
            } catch (RejectedExecutionException e) {
                log.error("任务提交被拒绝 (队列已满): {}", currentUrl, e);
                futures.add(CompletableFuture.completedFuture(BatchResult.rejected(currentUrl, "Queue full")));
            }
        }

        // ── 4. 收集结果（带超时） ──
        List<BatchResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<BatchResult> f = futures.get(i);
            String url = i < urls.size() ? urls.get(i) : "N/A";
            try {
                results.add(f.get(joinTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                log.error("任务执行超时: {}", url, e);
                f.cancel(true);
                results.add(BatchResult.unknown(url,
                        "Task timed out after " + joinTimeoutMs + "ms"));
            } catch (ExecutionException e) {
                log.error("任务执行异常", e);
                Throwable cause = e.getCause();
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                results.add(BatchResult.unknown(url, msg != null ? msg : "Task execution failed"));
            } catch (CancellationException e) {
                log.error("任务被取消", e);
                results.add(BatchResult.unknown(url, "Task cancelled"));
            } catch (InterruptedException e) {
                log.error("线程被中断", e);
                Thread.currentThread().interrupt();
                results.add(BatchResult.unknown(url, "Thread interrupted"));
            }
        }
        return results;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                if (!dropped.isEmpty()) {
                    log.warn("OkHttpBatchExecutor shutdown: {} queued tasks were cancelled (never started)",
                            dropped.size());
                }
            }
        } catch (InterruptedException e) {
            log.error("关闭 executor 时线程被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
