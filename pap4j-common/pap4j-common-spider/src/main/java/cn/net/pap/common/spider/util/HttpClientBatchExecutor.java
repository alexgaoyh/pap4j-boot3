package cn.net.pap.common.spider.util;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p><strong>HttpClientBatchExecutor</strong></p>
 * <p>基于 Apache HttpClient 5 实现的高效、资源受控的批量请求执行器。</p>
 */
public class HttpClientBatchExecutor implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(HttpClientBatchExecutor.class);
    private static final long DEFAULT_JOIN_TIMEOUT_MS = 30_000L;

    /**
     * 创建一个忽略 SSL 证书校验的 CloseableHttpClient
     */
    public static CloseableHttpClient createUnsafeHttpClient() {
        return createUnsafeHttpClient(null);
    }

    /**
     * 创建一个忽略 SSL 证书校验的 CloseableHttpClient，并附加默认请求头
     */
    public static CloseableHttpClient createUnsafeHttpClient(Map<String, String> headers) {
        try {
            SSLContext sslContext = SSLContexts.custom()
                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                    .build();
            SSLConnectionSocketFactory sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslContext)
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                    .build();
            PoolingHttpClientConnectionManager manager = PoolingHttpClientConnectionManagerBuilder.create()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();

            var builder = HttpClients.custom()
                    .setConnectionManager(manager);

            if (headers != null && !headers.isEmpty()) {
                List<org.apache.hc.core5.http.Header> defaultHeaders = new ArrayList<>();
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        defaultHeaders.add(new BasicHeader(entry.getKey(), entry.getValue()));
                    }
                }
                builder.setDefaultHeaders(defaultHeaders);
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Failed to create unsafe HttpClient", e);
            throw new RuntimeException("Failed to create unsafe HttpClient", e);
        }
    }

    /**
     * 同步发送单笔 POST 请求（JSON Body）并直接返回响应字符串内容
     */
    public static String executePost(CloseableHttpClient client, String url, String jsonBody) throws IOException {
        if (client == null) {
            throw new IOException("HttpClient is null");
        }
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));
        return client.execute(httpPost, response -> {
            String body = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
            if (response.getCode() < 200 || response.getCode() >= 300) {
                throw new IOException("HTTP " + response.getCode() + ": " + body);
            }
            return body;
        });
    }

    private final CloseableHttpClient client;
    private final ThreadPoolExecutor executor;
    private final int maxBatchSize;
    private final long joinTimeoutMs;

    public enum Status {
        SUCCESS,
        FAILED,
        REJECTED,
        UNKNOWN
    }

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

    public HttpClientBatchExecutor(CloseableHttpClient client, int corePoolSize, int maxPoolSize, int queueCapacity, int maxBatchSize) {
        this(client, corePoolSize, maxPoolSize, queueCapacity, maxBatchSize, DEFAULT_JOIN_TIMEOUT_MS);
    }

    HttpClientBatchExecutor(CloseableHttpClient client, int corePoolSize, int maxPoolSize, int queueCapacity, int maxBatchSize, long joinTimeoutMs) {
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
                        return new Thread(r, "httpclient-batch-" + counter.getAndIncrement());
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public List<BatchResult> executeBatch(String url, List<String> jsonBodies) {
        if (jsonBodies == null || jsonBodies.isEmpty()) {
            return Collections.emptyList();
        }

        if (maxBatchSize > 0 && jsonBodies.size() > maxBatchSize) {
            log.warn("批量请求数量 {} 超过上限 maxBatchSize={}，全部拒绝", jsonBodies.size(), maxBatchSize);
            List<BatchResult> results = new ArrayList<>(jsonBodies.size());
            for (String json : jsonBodies) {
                results.add(BatchResult.rejected(json, "Batch size " + jsonBodies.size() + " exceeds max " + maxBatchSize));
            }
            return results;
        }

        if (client == null) {
            log.warn("HttpClient 为 null，所有请求直接返回失败");
            List<BatchResult> results = new ArrayList<>(jsonBodies.size());
            for (String json : jsonBodies) {
                results.add(BatchResult.failure(json, "client null"));
            }
            return results;
        }

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(jsonBodies.size());
        for (String json : jsonBodies) {
            final String currentInput = json;
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String body = executePost(client, url, currentInput);
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

        List<BatchResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<BatchResult> f = futures.get(i);
            String input = i < jsonBodies.size() ? jsonBodies.get(i) : "N/A";
            try {
                results.add(f.get(joinTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                log.error("任务执行超时: {}", input, e);
                f.cancel(true);
                results.add(BatchResult.unknown(input, "Task timed out after " + joinTimeoutMs + "ms"));
            } catch (ExecutionException e) {
                log.error("任务执行异常", e);
                Throwable cause = e.getCause();
                String msg = cause != null ? cause.getMessage() : e.getMessage();
                results.add(BatchResult.unknown(input, msg != null ? msg : "Task execution failed"));
            } catch (CancellationException e) {
                log.error("任务被取消", e);
                results.add(BatchResult.unknown(input, "Task cancelled"));
            } catch (InterruptedException e) {
                log.error("线程被中断", e);
                Thread.currentThread().interrupt();
                results.add(BatchResult.unknown(input, "Thread interrupted"));
            }
        }
        return results;
    }

    public List<BatchResult> executeBatch(List<String> urls, String jsonBody) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptyList();
        }

        if (maxBatchSize > 0 && urls.size() > maxBatchSize) {
            log.warn("批量请求数量 {} 超过上限 maxBatchSize={}，全部拒绝", urls.size(), maxBatchSize);
            List<BatchResult> results = new ArrayList<>(urls.size());
            for (String url : urls) {
                results.add(BatchResult.rejected(url, "Batch size " + urls.size() + " exceeds max " + maxBatchSize));
            }
            return results;
        }

        if (client == null) {
            log.warn("HttpClient 为 null，所有请求直接返回失败");
            List<BatchResult> results = new ArrayList<>(urls.size());
            for (String url : urls) {
                results.add(BatchResult.failure(url, "client null"));
            }
            return results;
        }

        List<CompletableFuture<BatchResult>> futures = new ArrayList<>(urls.size());
        for (String url : urls) {
            final String currentUrl = url;
            try {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        String body = executePost(client, currentUrl, jsonBody);
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

        List<BatchResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<BatchResult> f = futures.get(i);
            String url = i < urls.size() ? urls.get(i) : "N/A";
            try {
                results.add(f.get(joinTimeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                log.error("任务执行超时: {}", url, e);
                f.cancel(true);
                results.add(BatchResult.unknown(url, "Task timed out after " + joinTimeoutMs + "ms"));
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
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("关闭 executor 时线程被中断", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        if (client != null) {
            try {
                client.close();
            } catch (IOException e) {
                log.error("Failed to close HttpClient", e);
            }
        }
    }
}
