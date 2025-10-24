package cn.net.pap.example.service.impl;

import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.event.WebhookEvent;
import cn.net.pap.example.service.IWebhookSenderService;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.List;

@Service
@EnableAsync
public class WebhookSenderServiceImpl implements IWebhookSenderService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookSenderServiceImpl.class);

    @Autowired
    private IWebhookSubscriptionService subscriptionService;

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
     */
    @Async
    @Override
    public void sendWebhookAsync(WebhookSubscription subscription, WebhookEvent event) {
        executeWithRetry(subscription, event, 0);
    }

    private void executeWithRetry(WebhookSubscription subscription,
                                  WebhookEvent event, int retryCount) {
        try {
            sendSingleWebhook(subscription, event);
            logger.info("Webhook 发送成功: {} -> {}",
                    subscription.getEventType(), subscription.getCallbackUrl());

        } catch (Exception e) {
            retryCount++;
            logger.warn("Webhook 发送失败，重试 {}/{}: {}",
                    retryCount, subscription.getRetryCount(), e.getMessage());

            if (retryCount <= subscription.getRetryCount()) {
                // 指数退避重试
                try {
                    long delay = 1000L * (long) Math.pow(2, retryCount - 1);
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                executeWithRetry(subscription, event, retryCount);
            } else {
                logger.error("Webhook 最终发送失败: {} -> {}",
                        subscription.getCallbackUrl(), e.getMessage());
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

}
