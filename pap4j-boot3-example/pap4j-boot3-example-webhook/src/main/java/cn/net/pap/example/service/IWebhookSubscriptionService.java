package cn.net.pap.example.service;

import cn.net.pap.example.entity.WebhookSubscription;

import java.util.List;

public interface IWebhookSubscriptionService {

    /**
     * 根据事件类型获取活跃的订阅
     */
    public List<WebhookSubscription> getActiveSubscriptions(String eventType);

    /**
     * 创建新的订阅
     */
    public WebhookSubscription createSubscription(String name, String callbackUrl,
                                                  String eventType, String secret);

}
