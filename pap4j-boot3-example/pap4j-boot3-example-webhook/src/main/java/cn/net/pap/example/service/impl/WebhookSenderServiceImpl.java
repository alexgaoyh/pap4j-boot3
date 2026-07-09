package cn.net.pap.example.service.impl;

import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.event.WebhookEvent;
import cn.net.pap.example.service.IWebhookSenderService;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Webhook 异步发送与重试优化服务实现类
 * <p>
 * 本类采用了非阻塞高并发架构重构，用以解决常规 Webhook 推送场景下的资源饥饿与崩溃隐患。
 * </p>
 * 
 * <h3>核心技术优势（Pros）：</h3>
 * <ul>
 *   <li><b>非阻塞异步重试</b>：废弃了阻塞工作线程的 {@code Thread.sleep(delay)}，改用 {@link CompletableFuture#delayedExecutor(long, TimeUnit, java.util.concurrent.Executor)}，
 *       使失败等待期间的工作线程能立即被释放并归还线程池，极大地提高了系统在高并发、网络动荡下的并发吞吐量。</li>
 *   <li><b>独立私有线程池</b>：线程池 {@code webhookTaskExecutor} 完全作为私有成员实例化与销毁，避免了多组件公用同一线程池造成的排查困难。</li>
 *   <li><b>主动背压流控</b>：配置 {@link CallerBlocksPolicy} 饱和策略。当队列满载时限制挂起调用方，从源头上保护 JVM 堆内存（防 OOM）。</li>
 * </ul>
 * 
 * <h3>架构折中与局限性（Cons）：</h3>
 * <ul>
 *   <li><b>内存易失性</b>：延迟任务暂存在 JVM 内存中。若在重试等待期间系统重启或异常断电，<b>这部分内存队列中的任务将会丢失</b>。</li>
 *   <li><b>内存积压风险</b>：在网络大面积瘫痪时，若有海量 Webhook 任务持续失败并注册重试，可能会在 JVM 堆中积压大量轻量级重试任务对象。</li>
 *   <li><b>缺乏可视化控制</b>：计时队列属于 JDK 内部黑盒，无法在监控管理端看到排队的任务数量、或是手动触发/取消重试。</li>
 * </ul>
 * 
 * <h3>架构选型建议：</h3>
 * 本方案非常适用于高吞吐量、对轻微丢失不敏感、但要求极高并发性能的 Webhook 回调推送场景。
 * 针对金融对账、扣款等<b>绝对要求持久化、重启不丢失</b>的核心业务场景，应采用<b>数据库驱动退避重试（如 Quartz TaskData 方案）</b>或 MQ 延迟死信队列。
 */
@Service
public class WebhookSenderServiceImpl implements IWebhookSenderService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSenderServiceImpl.class);

    private final IWebhookSubscriptionService subscriptionService;

    // 线程池完全私有化，由本类生命周期独立管理，避免全局共享与资源干涉
    private ThreadPoolTaskExecutor webhookTaskExecutor;

    public WebhookSenderServiceImpl(IWebhookSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * 初始化私有发送线程池
     * 【线程池参数设计】：
     * 1. corePoolSize=5, maxPoolSize=10: 支持最大 10 并发处理 Webhook 回调推送任务。
     * 2. queueCapacity=50: 采用有界队列限制任务积压，超出后激活背压。
     * 3. allowCoreThreadTimeOut=true, keepAliveSeconds=60: 允许空闲线程自动回收释放资源。
     * 4. 饱和策略：采用自定义内部类 CallerBlocksPolicy，超时 5 秒未放入队列则抛出拒绝异常。
     */
    @PostConstruct
    public void init() {
        logger.info("[Webhook-Sender] 正在初始化私有发送线程池...");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("webhook-task-");
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new CallerBlocksPolicy(5, TimeUnit.SECONDS));
        executor.initialize();
        this.webhookTaskExecutor = executor;
        logger.info("[Webhook-Sender] 私有发送线程池初始化完成");
    }

    /**
     * 创建带超时设置的 RestTemplate
     */
    private RestTemplate createRestTemplate(int timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    /**
     * 触发事件 - 发送给所有订阅者
     */
    @Override
    public void triggerEvent(String eventType, Object data) {
        List<WebhookSubscription> subscriptions =
                subscriptionService.getActiveSubscriptions(eventType);

        if (subscriptions.isEmpty()) {
            logger.info("事件 {} 没有订阅者", eventType);
            return;
        }

        WebhookEvent event = new WebhookEvent(eventType, data);

        subscriptions.forEach(subscription -> {
            sendWebhookAsync(subscription, event);
        });

        logger.info("事件 {} 已发送给 {} 个订阅者", eventType, subscriptions.size());
    }

    /**
     * 异步发送 Webhook
     * [ADJUSTED] 去除了 Spring 的 @Async 注解，改由底层的私有线程池直接异步执行，避开 Spring 代理，完全受控。
     */
    @Override
    public void sendWebhookAsync(WebhookSubscription subscription, WebhookEvent event) {
        try {
            webhookTaskExecutor.execute(() -> executeWithRetry(subscription, event, 0));
        } catch (RejectedExecutionException e) {
            logger.error("[Webhook-Sender] 无法提交 Webhook 异步发送任务，队列满载且等待超时，URL: {}",
                    subscription.getCallbackUrl(), e);
            saveWebhookDeliveryLog(subscription, event, "FAILED", "Thread pool saturated: " + e.getMessage());
        }
    }

    /**
     * 递归执行带指数退避重试的发送逻辑
     * [ADJUSTED] 移除了 Thread.sleep() 阻塞调用，改为使用 CompletableFuture.delayedExecutor 实现非阻塞延迟重试。
     */
    private void executeWithRetry(WebhookSubscription subscription,
                                  WebhookEvent event, int retryCount) {
        try {
            sendSingleWebhook(subscription, event);
            logger.info("Webhook 发送成功: {} -> {}",
                    subscription.getEventType(), subscription.getCallbackUrl());

        } catch (Exception e) {
            int nextRetry = retryCount + 1;
            logger.warn("Webhook 发送失败，准备重试 {}/{}: {}",
                    nextRetry, subscription.getRetryCount(), e.getMessage());

            if (nextRetry <= subscription.getRetryCount()) {
                // 计算指数延迟时间：1000ms, 2000ms, 4000ms...
                long delay = 1000L * (long) Math.pow(2, nextRetry - 1);

                // [ADJUSTED] 非阻塞式延迟调度执行下一次重试，不占用当前工作线程
                CompletableFuture.runAsync(
                        () -> executeWithRetry(subscription, event, nextRetry),
                        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS, webhookTaskExecutor)
                );
                logger.info("[Webhook-Sender] 已成功调度延时重试任务，延迟: {} ms, URL: {}", delay, subscription.getCallbackUrl());
            } else {
                logger.error("Webhook 最终发送失败: {}", subscription.getCallbackUrl(), e);
                saveWebhookDeliveryLog(subscription, event, "FAILED", e.getMessage());
            }
        }
    }

    private void sendSingleWebhook(WebhookSubscription subscription,
                                   WebhookEvent event) {
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "PAP-Webhook/1.0");
        headers.set("X-Event-ID", event.getEventId());
        headers.set("X-Event-Type", event.getEventType());
        headers.set("X-Delivery-Time", String.valueOf(System.currentTimeMillis()));

        // 添加签名
        if (StringUtils.hasText(subscription.getSecret())) {
            String signature = generateSignature(event, subscription.getSecret());
            headers.set("X-Signature", signature);
        }

        // 构建请求
        HttpEntity<WebhookEvent> request = new HttpEntity<>(event, headers);

        // 创建带超时的 RestTemplate
        RestTemplate restTemplate = createRestTemplate(subscription.getTimeout());

        // 发送请求
        ResponseEntity<String> response = restTemplate.exchange(
                subscription.getCallbackUrl(),
                HttpMethod.POST,
                request,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("HTTP " + response.getStatusCodeValue());
        }

        saveWebhookDeliveryLog(subscription, event, "SUCCESS", null);
    }

    /**
     * 生成签名
     */
    private String generateSignature(WebhookEvent event, String secret) {
        try {
            String data = event.getEventId() + event.getEventType() + event.getTimestamp();
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secretKey);

            return Base64.getEncoder().encodeToString(
                    sha256_HMAC.doFinal(data.getBytes())
            );
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    private void saveWebhookDeliveryLog(WebhookSubscription subscription,
                                        WebhookEvent event,
                                        String status,
                                        String errorMessage) {
        logger.debug("Webhook 投递日志 - 订阅: {}, 事件: {}, 状态: {}, 错误: {}",
                subscription.getId(), event.getEventId(), status, errorMessage);
    }

    /**
     * 优雅关闭私有线程池
     */
    @PreDestroy
    public void destroy() {
        logger.info("[Webhook-Sender] 正在优雅关闭私有线程池...");
        if (webhookTaskExecutor != null) {
            webhookTaskExecutor.shutdown();
            try {
                if (!webhookTaskExecutor.getThreadPoolExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("[Webhook-Sender] 线程池在 5 秒内未完全终止，强制关闭中...");
                    webhookTaskExecutor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                webhookTaskExecutor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("[Webhook-Sender] 私有线程池优雅关闭完成");
    }

    /**
     * [ADJUSTED] 阻塞提交拒绝策略内部类，当有界队列满且工作线程达上限时，阻塞调用线程防堆内存膨胀
     */
    private static class CallerBlocksPolicy implements RejectedExecutionHandler {
        private final long timeout;
        private final TimeUnit unit;

        public CallerBlocksPolicy(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                throw new RejectedExecutionException("Executor has been shut down");
            }
            try {
                if (timeout > 0) {
                    logger.warn("[Webhook-Backpressure] 队列满载，提交线程开始阻塞等待... 限时 {} {}", timeout, unit);
                    boolean success = executor.getQueue().offer(r, timeout, unit);
                    if (!success) {
                        logger.error("[Webhook-Backpressure] 队列持续满载，超时未成功，拒绝执行新任务");
                        throw new RejectedExecutionException("Task submission timed out");
                    }
                } else {
                    logger.warn("[Webhook-Backpressure] 队列满载，提交线程开始无限期阻塞等待...");
                    executor.getQueue().put(r);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RejectedExecutionException("Thread interrupted waiting for queue space", e);
            }
        }
    }

}
