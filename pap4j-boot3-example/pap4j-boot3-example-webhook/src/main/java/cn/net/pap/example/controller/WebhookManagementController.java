package cn.net.pap.example.controller;

import cn.net.pap.example.dto.CreateSubscriptionRequestDTO;
import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.service.IWebhookSenderService;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class WebhookManagementController {

    @Autowired
    private IWebhookSubscriptionService subscriptionService;

    @Autowired
    private IWebhookSenderService webhookSender;

    /**
     * 创建订阅
     */
    @PostMapping("/subscriptions")
    public WebhookSubscription createSubscription(@RequestBody CreateSubscriptionRequestDTO request) {
        return subscriptionService.createSubscription(
                request.getName(),
                request.getCallbackUrl(),
                request.getEventType(),
                request.getSecret()
        );
    }

    /**
     * 测试事件触发
     */
    @PostMapping("/test/{eventType}")
    public ResponseEntity<String> testEvent(@PathVariable String eventType,
                                            @RequestBody Map<String, Object> testData) {
        webhookSender.triggerEvent(eventType, testData);
        return ResponseEntity.ok("测试事件已触发");
    }

    /**
     * 获取订阅
     */
    @GetMapping("/subscriptions/{eventType}")
    public List<WebhookSubscription> listSubscriptions(@PathVariable String eventType) {
        return subscriptionService.getActiveSubscriptions(eventType);
    }

}
