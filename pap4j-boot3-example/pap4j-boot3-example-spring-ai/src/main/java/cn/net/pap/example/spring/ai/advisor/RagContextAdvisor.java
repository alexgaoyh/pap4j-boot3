package cn.net.pap.example.spring.ai.advisor;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

public class RagContextAdvisor implements CallAdvisor, StreamAdvisor {

    public static final String RAG_CONTEXT_KEY = "rag_context";

    @Override
    public String getName() {
        return "RagContextAdvisor";
    }

    @Override
    public int getOrder() {
        // Execute after MessageChatMemoryAdvisor (default order is typically 0)
        // so that MessageChatMemoryAdvisor sees the original query without context.
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return chain.nextCall(injectRagContext(request));
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(injectRagContext(request));
    }

    private ChatClientRequest injectRagContext(ChatClientRequest request) {
        String ragContext = (String) request.context().get(RAG_CONTEXT_KEY);
        if (ragContext == null || ragContext.isBlank()) {
            return request;
        }

        String originalUserText = request.prompt().getUserMessage().getText();
        String augmentedUserText = """
                使用以下提供的上下文信息来回答用户的问题。
                ---------------------
                上下文:
                %s
                ---------------------
                用户问题: %s
                """.formatted(ragContext, originalUserText);

        Prompt newPrompt = request.prompt().augmentUserMessage(old -> new UserMessage(augmentedUserText));

        return request.mutate()
                .prompt(newPrompt)
                .build();
    }
}
