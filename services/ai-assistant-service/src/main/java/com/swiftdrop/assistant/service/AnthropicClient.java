package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.assistant.domain.ChatMessage;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Anthropic Messages API over RestClient ({@code LLM_PROVIDER=anthropic}). Spring AI 1.1 (the line for Spring Boot 3.5)
 * always sends a temperature, which current Claude models reject, so the request is built by hand.
 * <p>
 * No {@code thinking} field: current Opus models run adaptive thinking by default. No sampling parameters.
 * {@code fallbacks: "default"} lets the API re-run a request its safety classifiers decline on a suitable model.
 */
public class AnthropicClient implements LlmClient {

    static final String BASE_URL = "https://api.anthropic.com";
    static final String DEFAULT_MODEL = "claude-opus-5-5";
    static final int MAX_TOKENS = 4096;
    private static final String API_VERSION = "2023-06-01";
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public AnthropicClient(RestClient.Builder builder, ObjectMapper json, String baseUrl, String apiKey, String model) {
        this.http = LlmClient.http(builder, baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", API_VERSION)
                .defaultHeader("anthropic-beta", FALLBACK_BETA)
                .build();
        this.json = json;
        this.apiKey = apiKey;
        this.model = model;
    }

    String model() {
        return model;
    }

    @Override
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public Conversation start(String system, List<ChatMessage> history, String message, List<ParcelTools.Spec> tools) {
        ObjectNode body = json.createObjectNode()
                .put("model", model)
                .put("max_tokens", MAX_TOKENS)
                .put("system", system)
                .put("fallbacks", "default");
        ArrayNode messages = body.putArray("messages");
        for (ChatMessage m : history) {
            messages.addObject().put("role", m.getRole()).put("content", m.getContent());
        }
        messages.addObject().put("role", "user").put("content", message);
        ArrayNode defs = body.putArray("tools");
        for (ParcelTools.Spec spec : tools) {
            defs.addObject().put("name", spec.name()).put("description", spec.description())
                    .set("input_schema", spec.inputSchema());
        }
        return new Turn(body, messages);
    }

    private final class Turn implements Conversation {

        private final ObjectNode body;
        private final ArrayNode messages;
        private JsonNode lastContent;
        private List<ToolCall> lastCalls = List.of();

        Turn(ObjectNode body, ArrayNode messages) {
            this.body = body;
            this.messages = messages;
        }

        @Override
        public Step next() {
            JsonNode response = http.post().uri("/v1/messages").body(body).retrieve().body(JsonNode.class);
            String stop = response.path("stop_reason").asText();
            if ("refusal".equals(stop)) {
                return new Step("", List.of(), true, stop);
            }
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode block : response.path("content")) {
                switch (block.path("type").asText()) {
                    case "tool_use" -> calls.add(new ToolCall(block.path("id").asText(), block.path("name").asText(), block.get("input")));
                    case "text" -> text.append(block.path("text").asText());
                    default -> { } // thinking blocks: kept in lastContent, never shown
                }
            }
            lastContent = response.get("content");
            lastCalls = "tool_use".equals(stop) ? calls : List.of();
            return new Step(text.toString().trim(), lastCalls, false, stop);
        }

        /** The assistant turn goes back unchanged (thinking signatures included), then every tool result in one message. */
        @Override
        public void answer(List<ParcelTools.Result> results) {
            messages.addObject().put("role", "assistant").set("content", lastContent);
            ArrayNode blocks = json.createArrayNode();
            for (int i = 0; i < lastCalls.size(); i++) {
                ParcelTools.Result result = results.get(i);
                ObjectNode block = blocks.addObject()
                        .put("type", "tool_result")
                        .put("tool_use_id", lastCalls.get(i).id())
                        .put("content", result.text());
                if (result.isError()) {
                    block.put("is_error", true);
                }
            }
            messages.addObject().put("role", "user").set("content", blocks);
        }
    }
}
