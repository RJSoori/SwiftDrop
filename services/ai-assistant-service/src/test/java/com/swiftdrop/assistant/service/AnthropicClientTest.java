package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.swiftdrop.assistant.domain.ChatMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;

/** LLM_PROVIDER=anthropic: one tool round trip against WireMock playing the Messages API. */
class AnthropicClientTest {

    private static final WireMockServer api = new WireMockServer(wireMockConfig().dynamicPort());
    private final ObjectMapper json = new ObjectMapper();

    static {
        api.start();
    }

    @AfterAll
    static void stop() {
        api.stop();
    }

    @BeforeEach
    void reset() {
        api.resetAll();
    }

    private AnthropicClient client() {
        return new AnthropicClient(RestClient.builder(), json, api.baseUrl(), "test-key", AnthropicClient.DEFAULT_MODEL);
    }

    @Test
    void toolRoundTripEchoesTheTurnAndSendsTheResult() throws Exception {
        api.stubFor(post("/v1/messages").inScenario("t").whenScenarioStateIs(STARTED).willReturn(okJson("""
                {"stop_reason":"tool_use","content":[{"type":"thinking","thinking":"","signature":"sig-abc"},
                 {"type":"tool_use","id":"toolu_1","name":"track_parcel","input":{"trackingNo":"SD-1001"}}]}"""))
                .willSetStateTo("done"));
        api.stubFor(post("/v1/messages").inScenario("t").whenScenarioStateIs("done").willReturn(okJson("""
                {"stop_reason":"end_turn","content":[{"type":"text","text":"Out for delivery."}]}""")));
        List<ChatMessage> history = List.of(
                new ChatMessage(UUID.randomUUID(), ChatMessage.USER, "Hi", null),
                new ChatMessage(UUID.randomUUID(), ChatMessage.ASSISTANT, "Hello", null));
        ParcelTools tools = new ParcelTools(RestClient.builder(), "http://127.0.0.1:1", json);

        LlmClient.Conversation conversation = client().start("system", history, "Where is SD-1001?", tools.specs());
        LlmClient.Step step = conversation.next();
        assertThat(step.calls()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo("toolu_1");
            assertThat(c.name()).isEqualTo("track_parcel");
            assertThat(c.input().path("trackingNo").asText()).isEqualTo("SD-1001");
        });
        conversation.answer(List.of(new ParcelTools.Result("{\"status\":\"OUT_FOR_DELIVERY\"}", true)));
        LlmClient.Step last = conversation.next();
        assertThat(last.calls()).isEmpty();
        assertThat(last.text()).isEqualTo("Out for delivery.");
        assertThat(last.refused()).isFalse();

        api.verify(postRequestedFor(urlEqualTo("/v1/messages"))
                .withHeader("x-api-key", equalTo("test-key"))
                .withHeader("anthropic-version", equalTo("2023-06-01")));
        List<LoggedRequest> requests = api.findAll(postRequestedFor(urlEqualTo("/v1/messages")));
        JsonNode first = json.readTree(requests.get(0).getBodyAsString());
        assertThat(first.path("model").asText()).isEqualTo("claude-opus-5-5");
        assertThat(first.has("temperature")).isFalse();
        assertThat(first.path("tools").findValuesAsText("name")).contains("track_parcel", "quote_fee", "list_my_parcels");
        JsonNode messages = json.readTree(requests.get(1).getBodyAsString()).path("messages");
        assertThat(messages.findValuesAsText("role")).containsExactly("user", "assistant", "user", "assistant", "user");
        assertThat(messages.get(3).path("content").get(0).path("signature").asText()).isEqualTo("sig-abc");
        JsonNode result = messages.get(4).path("content").get(0);
        assertThat(result.path("type").asText()).isEqualTo("tool_result");
        assertThat(result.path("tool_use_id").asText()).isEqualTo("toolu_1");
        assertThat(result.path("is_error").asBoolean()).isTrue();
    }

    @Test
    void refusalStopReasonIsARefusal() {
        api.stubFor(post("/v1/messages").willReturn(okJson("{\"stop_reason\":\"refusal\",\"content\":[]}")));

        LlmClient.Step step = client().start("system", List.of(), "hi", List.of()).next();

        assertThat(step.refused()).isTrue();
        assertThat(step.calls()).isEmpty();
    }
}
