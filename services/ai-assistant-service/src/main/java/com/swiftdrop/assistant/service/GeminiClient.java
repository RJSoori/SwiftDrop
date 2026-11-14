package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.assistant.domain.ChatMessage;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gemini API {@code generateContent} with function calling, over RestClient ({@code LLM_PROVIDER=gemini}, the default).
 * <p>
 * The model's {@code content} goes back unchanged in the next request, so any {@code thoughtSignature} on its parts
 * is kept (Gemini rejects a turn with a missing signature). No generationConfig: the model's own defaults apply.
 */
public class GeminiClient implements LlmClient {

    static final String BASE_URL = "https://generativelanguage.googleapis.com";
    /** Stable, on the free tier, supports function calling (Gemini API model and pricing pages, Nov 2026). */
    static final String DEFAULT_MODEL = "gemini-3.8-flash";
    /** finishReason values meaning the model would not answer. */
    private static final Set<String> BLOCKED = Set.of("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII");

    private final RestClient http;
    private final ObjectMapper json;
    private final String apiKey;
    private final String model;

    public GeminiClient(RestClient.Builder builder, ObjectMapper json, String baseUrl, String apiKey, String model) {
        this.http = LlmClient.http(builder, baseUrl).defaultHeader("x-goog-api-key", apiKey).build();
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
        ObjectNode body = json.createObjectNode();
        body.putObject("systemInstruction").putArray("parts").addObject().put("text", system);
        ArrayNode contents = body.putArray("contents");
        for (ChatMessage m : history) {
            text(contents, ChatMessage.USER.equals(m.getRole()) ? "user" : "model", m.getContent());
        }
        text(contents, "user", message);
        ArrayNode declarations = body.putArray("tools").addObject().putArray("functionDeclarations");
        for (ParcelTools.Spec spec : tools) {
            ObjectNode d = declarations.addObject().put("name", spec.name()).put("description", spec.description());
            if (!spec.inputSchema().path("properties").isEmpty()) {
                d.set("parameters", schema(spec.inputSchema())); // a tool without arguments declares no parameters
            }
        }
        return new Turn(body, contents);
    }

    private static void text(ArrayNode contents, String role, String text) {
        contents.addObject().put("role", role).putArray("parts").addObject().put("text", text);
    }

    /** Our JSON Schema in Gemini's Schema form: upper-case types, no additionalProperties (not supported there). */
    private JsonNode schema(JsonNode node) {
        if (!node.isObject()) {
            return node.deepCopy();
        }
        ObjectNode out = json.createObjectNode();
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            switch (field.getKey()) {
                case "additionalProperties" -> { }
                case "type" -> out.put("type", field.getValue().asText().toUpperCase(Locale.ROOT));
                case "properties" -> {
                    ObjectNode props = out.putObject("properties");
                    field.getValue().properties().forEach(p -> props.set(p.getKey(), schema(p.getValue())));
                }
                case "items" -> out.set("items", schema(field.getValue()));
                default -> out.set(field.getKey(), field.getValue().deepCopy());
            }
        }
        return out;
    }

    private final class Turn implements Conversation {

        private final ObjectNode body;
        private final ArrayNode contents;
        private JsonNode lastContent;
        private List<ToolCall> lastCalls = List.of();

        Turn(ObjectNode body, ArrayNode contents) {
            this.body = body;
            this.contents = contents;
        }

        @Override
        public Step next() {
            JsonNode response = http.post().uri("/v1beta/models/{model}:generateContent", model)
                    .body(body).retrieve().body(JsonNode.class);
            String blocked = response.path("promptFeedback").path("blockReason").asText("");
            if (!blocked.isEmpty()) {
                return new Step("", List.of(), true, "prompt:" + blocked);
            }
            JsonNode candidate = response.path("candidates").path(0);
            String finish = candidate.path("finishReason").asText();
            if (BLOCKED.contains(finish)) {
                return new Step("", List.of(), true, finish);
            }
            List<ToolCall> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            for (JsonNode part : candidate.path("content").path("parts")) {
                JsonNode call = part.get("functionCall");
                if (call != null) {
                    calls.add(new ToolCall(call.path("id").asText(null), call.path("name").asText(), call.get("args")));
                } else if (part.has("text") && !part.path("thought").asBoolean(false)) {
                    text.append(part.path("text").asText());
                }
            }
            lastContent = candidate.path("content");
            lastCalls = calls;
            return new Step(text.toString().trim(), calls, false, finish);
        }

        /** The model turn goes back unchanged, then one user turn with a functionResponse per call. */
        @Override
        public void answer(List<ParcelTools.Result> results) {
            contents.add(lastContent);
            ArrayNode parts = contents.addObject().put("role", "user").putArray("parts");
            for (int i = 0; i < lastCalls.size(); i++) {
                ToolCall call = lastCalls.get(i);
                ParcelTools.Result result = results.get(i);
                ObjectNode response = parts.addObject().putObject("functionResponse").put("name", call.name());
                if (call.id() != null) {
                    response.put("id", call.id());
                }
                response.putObject("response").put(result.isError() ? "error" : "result", result.text());
            }
        }
    }
}
