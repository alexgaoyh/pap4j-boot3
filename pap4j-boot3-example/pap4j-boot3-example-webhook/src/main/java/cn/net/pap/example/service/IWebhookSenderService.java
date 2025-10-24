package cn.net.pap.example.service;

import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.event.WebhookEvent;

public interface IWebhookSenderService {


    public void triggerEvent(String eventType, Object data);

    /**
     * 异步发送 Webhook
     */
    public void sendWebhookAsync(WebhookSubscription subscription, WebhookEvent event);


}
