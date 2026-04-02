package cn.net.pap.example.controller;

import cn.net.pap.example.dto.CreateSubscriptionRequestDTO;
import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.service.IWebhookSenderService;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/webhook")
public class WebhookManagementController {

    private final IWebhookSubscriptionService subscriptionService;

    private final IWebhookSenderService webhookSender;

    public WebhookManagementController(IWebhookSubscriptionService subscriptionService, IWebhookSenderService webhookSender) {
        this.subscriptionService = subscriptionService;
        this.webhookSender = webhookSender;
    }

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

    @RequestMapping("/test/webhook")
    public ResponseEntity<String> testWebhook(HttpServletRequest request, HttpServletResponse response) {
        try {
            Enumeration<String> headerNames = request.getHeaderNames();
            Map<String, String> headers = new HashMap<>();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
            System.out.println("Headers: " + headers);

            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            System.out.println("Body: " + body);

            return ResponseEntity.ok("");
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /**
     * 获取订阅
     */
    @GetMapping("/subscriptions/{eventType}")
    public List<WebhookSubscription> listSubscriptions(@PathVariable String eventType) {
        return subscriptionService.getActiveSubscriptions(eventType);
    }

}
