package cn.net.pap.example.controller;

import cn.net.pap.example.dto.CreateSubscriptionRequestDTO;
import cn.net.pap.example.entity.WebhookSubscription;
import cn.net.pap.example.service.IWebhookSenderService;
import cn.net.pap.example.service.IWebhookSubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Webhook管理接口", description = "提供 Webhook 订阅创建、事件测试触发、订阅列表获取等管理功能的接口")
public class WebhookManagementController {

    private static final Logger log = LoggerFactory.getLogger(WebhookManagementController.class);

    private final IWebhookSubscriptionService subscriptionService;

    private final IWebhookSenderService webhookSender;

    public WebhookManagementController(IWebhookSubscriptionService subscriptionService, IWebhookSenderService webhookSender) {
        this.subscriptionService = subscriptionService;
        this.webhookSender = webhookSender;
    }

    /**
     * 创建订阅
     */
    @Operation(summary = "创建 Webhook 订阅")
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
    @Operation(summary = "测试触发 Webhook 事件")
    @PostMapping("/test/{eventType}")
    public ResponseEntity<String> testEvent(@Parameter(description = "事件类型") @PathVariable String eventType,
                                            @RequestBody Map<String, Object> testData) {
        webhookSender.triggerEvent(eventType, testData);
        return ResponseEntity.ok("测试事件已触发");
    }

    @Operation(summary = "测试接收 Webhook 回调（回显）", description = "用于模拟和打印接收到的 Webhook 请求头和请求体。")
    @RequestMapping("/test/webhook")
    public ResponseEntity<String> testWebhook(HttpServletRequest request, HttpServletResponse response) {
        try {
            Enumeration<String> headerNames = request.getHeaderNames();
            Map<String, String> headers = new HashMap<>();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                headers.put(headerName, request.getHeader(headerName));
            }
            log.info("Headers: {}", headers);

            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            log.info("Body: {}", body);

            return ResponseEntity.ok("");
        } catch (IOException e) {
            log.error("Failed to handle webhook callback", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    /**
     * 获取订阅
     */
    @Operation(summary = "获取指定事件类型的活跃订阅列表")
    @GetMapping("/subscriptions/{eventType}")
    public List<WebhookSubscription> listSubscriptions(@Parameter(description = "事件类型") @PathVariable String eventType) {
        return subscriptionService.getActiveSubscriptions(eventType);
    }

}
