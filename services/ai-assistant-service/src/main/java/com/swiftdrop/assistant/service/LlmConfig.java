package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/** {@code LLM_PROVIDER} picks the model provider. A blank {@code LLM_MODEL} or base URL means the provider's default. */
@Configuration
public class LlmConfig {

    @Bean
    LlmClient llmClient(RestClient.Builder builder, ObjectMapper json,
                        @Value("${swiftdrop.llm.provider:gemini}") String provider,
                        @Value("${swiftdrop.llm.api-key:}") String apiKey,
                        @Value("${swiftdrop.llm.model:}") String model,
                        @Value("${swiftdrop.llm.base-url:}") String baseUrl) {
        return create(builder, json, provider, apiKey, model, baseUrl);
    }

    static LlmClient create(RestClient.Builder builder, ObjectMapper json, String provider, String apiKey, String model,
                            String baseUrl) {
        return switch (provider.trim().toLowerCase(Locale.ROOT)) {
            case "gemini" -> new GeminiClient(builder, json, or(baseUrl, GeminiClient.BASE_URL), apiKey,
                    or(model, GeminiClient.DEFAULT_MODEL));
            case "anthropic" -> new AnthropicClient(builder, json, or(baseUrl, AnthropicClient.BASE_URL), apiKey,
                    or(model, AnthropicClient.DEFAULT_MODEL));
            default -> throw new IllegalStateException("LLM_PROVIDER must be gemini or anthropic, not '" + provider + "'");
        };
    }

    private static String or(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
