package cn.net.pap.example.spring.ai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ChatClient chatClient;

    public AiController(ChatClient customChatClient) {
        this.chatClient = customChatClient;
    }

    /**
     * 响应式流式对话接口
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestParam String prompt) {
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content();
    }
}