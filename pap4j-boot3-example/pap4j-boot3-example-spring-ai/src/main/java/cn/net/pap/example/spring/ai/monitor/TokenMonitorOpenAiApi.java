package cn.net.pap.example.spring.ai.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 在 HTTP 协议层记录每一次 LLM 请求的原始 Token 用量（含缓存命中）。
 * <p>
 * 之所以挂在 {@link OpenAiApi} 而不是 ChatModel：Spring AI 在多轮工具调用时会把后续轮次的 Usage
 * 累加成新的 DefaultUsage 并丢弃原生对象，导致 cachedTokens 只能拿到第一轮。这里拿到的是服务端原始返回，
 * 每次请求（含每一轮工具调用）各打印一行，按 chatId 汇总即可得到整次对话成本。
 */
public class TokenMonitorOpenAiApi extends OpenAiApi {

    private static final Logger log = LoggerFactory.getLogger(TokenMonitorOpenAiApi.class);

    /**
     * Reactor Context 中传递会话 ID 的键
     */
    public static final String CHAT_ID_KEY = "TOKEN_MONITOR_CHAT_ID";

    private static final String DEFAULT_CHAT_ID = "default";

    private final String label;

    /**
     * @param baseUrl    服务地址
     * @param apiKey     API Key
     * @param restClient 同步请求客户端构建器
     * @param webClient  流式请求客户端构建器
     * @param label      模型用途标签，用于日志区分
     */
    public TokenMonitorOpenAiApi(String baseUrl, String apiKey, RestClient.Builder restClient,
                                 WebClient.Builder webClient, String label) {
        super(baseUrl, new SimpleApiKey(apiKey), new LinkedMultiValueMap<>(), "/v1/chat/completions", "/v1/embeddings",
                restClient, webClient, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
        this.label = label;
    }

    @Override
    public ResponseEntity<ChatCompletion> chatCompletionEntity(ChatCompletionRequest request,
                                                               MultiValueMap<String, String> headers) {
        ResponseEntity<ChatCompletion> response = super.chatCompletionEntity(request, headers);
        ChatCompletion body = response.getBody();
        record(DEFAULT_CHAT_ID, request.model(), body != null ? body.usage() : null);
        return response;
    }

    @Override
    public Flux<ChatCompletionChunk> chatCompletionStream(ChatCompletionRequest request,
                                                          MultiValueMap<String, String> headers) {
        return Flux.deferContextual(ctx -> {
            String chatId = ctx.getOrDefault(CHAT_ID_KEY, DEFAULT_CHAT_ID);
            AtomicReference<Usage> usage = new AtomicReference<>();
            return super.chatCompletionStream(request, headers)
                    .doOnNext(chunk -> {
                        if (chunk.usage() != null) {
                            usage.set(chunk.usage());
                        }
                    })
                    .doFinally(signal -> record(chatId, request.model(), usage.get()));
        });
    }

    private void record(String chatId, String model, Usage usage) {
        if (usage == null) {
            return;
        }
        int prompt = orZero(usage.promptTokens());
        int cached = usage.promptTokensDetails() != null ? orZero(usage.promptTokensDetails().cachedTokens()) : 0;
        int reasoning = usage.completionTokenDetails() != null ? orZero(usage.completionTokenDetails().reasoningTokens()) : 0;
        log.info("[TokenMonitor] chatId={}, label={}, model={}, prompt={}, cached={}, hitRate={}, completion={}, reasoning={}, total={}",
                chatId, label, model, prompt, cached, String.format("%.2f%%", prompt > 0 ? 100.0 * cached / prompt : 0.0),
                orZero(usage.completionTokens()), reasoning, orZero(usage.totalTokens()));
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }
}
