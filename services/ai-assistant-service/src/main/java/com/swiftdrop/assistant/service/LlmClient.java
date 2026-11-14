package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.swiftdrop.assistant.domain.ChatMessage;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * The chat model behind the assistant. {@link LlmConfig} picks the provider from {@code LLM_PROVIDER}:
 * {@link GeminiClient} (default) or {@link AnthropicClient}. {@link ChatService} runs the tool loop the same way for both.
 */
public interface LlmClient {

    /** False without an API key: the assistant then answers with a fixed reply and never calls the model. */
    boolean configured();

    /**
     * Starts one chat turn. {@code history} holds earlier user and assistant messages, oldest first, starting with
     * a user message; {@code message} is the customer's new message.
     */
    Conversation start(String system, List<ChatMessage> history, String message, List<ParcelTools.Spec> tools);

    interface Conversation {

        /** One model call. Throws RestClientException on any HTTP or connection failure. */
        Step next();

        /** Gives the model the results of the tool calls from the last step, in the same order. */
        void answer(List<ParcelTools.Result> results);
    }

    record ToolCall(String id, String name, JsonNode input) {
    }

    /** {@code calls} is empty when the model has finished; {@code stop} is the provider's own reason, for logs. */
    record Step(String text, List<ToolCall> calls, boolean refused, String stop) {
    }

    /** HTTP/1.1 only: the JDK client's h2c upgrade drops POST bodies on plain http (our WireMock tests). */
    static RestClient.Builder http(RestClient.Builder builder, String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        return builder.clone().baseUrl(baseUrl).requestFactory(factory);
    }
}
