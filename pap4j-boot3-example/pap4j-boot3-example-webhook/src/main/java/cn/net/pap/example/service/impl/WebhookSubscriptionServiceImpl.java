package cn.net.pap.example.service.impl;

import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.repository.WebhookSubscriptionRepository;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WebhookSubscriptionServiceImpl implements IWebhookSubscriptionService {

    private final WebhookSubscriptionRepository subscriptionRepository;

    public WebhookSubscriptionServiceImpl(WebhookSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /**
     * 根据事件类型获取活跃的订阅
     */
    public List<WebhookSubscription> getActiveSubscriptions(String eventType) {
        return subscriptionRepository.findByEventTypeAndActiveTrue(eventType);
    }

    /**
     * 创建新的订阅
     */
    public WebhookSubscription createSubscription(String name, String callbackUrl,
                                                  String eventType, String secret) {
        WebhookSubscription subscription = new WebhookSubscription();
        subscription.setName(name);
        subscription.setCallbackUrl(callbackUrl);
        subscription.setEventType(eventType);
        subscription.setSecret(secret);
        subscription.setActive(true);

        return subscriptionRepository.save(subscription);
    }

}
