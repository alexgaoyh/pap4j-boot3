package cn.net.pap.example.service;

import cn.net.pap.example.entity.WebhookSubscription;

import java.util.List;

public interface IWebhookSubscriptionService {

    /**
     * 根据事件类型获取活跃的订阅
     * @param eventType
     * @return 结果集合
     */
    public List<WebhookSubscription> getActiveSubscriptions(String eventType);

    /**
     * 创建新的订阅
     * @param name
     * @param callbackUrl
     * @param eventType
     * @param secret
     * @return 处理结果对象
     */
    public WebhookSubscription createSubscription(String name, String callbackUrl,
                                                  String eventType, String secret);

}
