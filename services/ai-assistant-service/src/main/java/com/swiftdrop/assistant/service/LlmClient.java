package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Anthropic Messages API over RestClient. Spring AI 1.1 (the line for Spring Boot 3.5) always sends a temperature,
 * which current Claude models reject, so the request is built by hand.
 * <p>
 * No {@code thinking} field: current Opus models run adaptive thinking by default. No sampling parameters.
 * {@code fallbacks: "default"} lets the API re-run a request its safety classifiers decline on a suitable model.
 */
@Component
public class LlmClient {

    static final int MAX_TOKENS = 4096;
    private static final String API_VERSION = "2023-06-01";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public LlmClient(RestClient.Builder builder, ObjectMapper json,
                     @Value("${swiftdrop.llm.base-url}") String baseUrl,
                     @Value("${swiftdrop.llm.api-key}") String apiKey,
                     @Value("${swiftdrop.llm.model}") String model) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1) // no h2c upgrade: it drops POST bodies on plain http
                        .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.http = builder.clone().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", API_VERSION)
                .defaultHeader("anthropic-beta", FALLBACK_BETA)
                .build();
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** One Messages API call. Throws RestClientException on any HTTP or connection failure. */
    public JsonNode send(String system, ArrayNode messages, ArrayNode tools) {
        ObjectNode body = json.createObjectNode()
                .put("model", model)
                .put("max_tokens", MAX_TOKENS)
                .put("system", system)
                .put("fallbacks", "default");
        body.set("messages", messages);
        body.set("tools", tools);
        return http.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class);
    }
}
