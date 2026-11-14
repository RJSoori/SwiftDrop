package com.swiftdrop.assistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.swiftdrop.assistant.domain.ChatMessage;
import com.swiftdrop.assistant.domain.ChatSession;
import com.swiftdrop.assistant.repository.ChatMessageRepository;
import com.swiftdrop.assistant.repository.ChatSessionRepository;
import com.swiftdrop.assistant.service.ChatService;
import com.swiftdrop.testsupport.PostgisContainerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat and MCP end to end inside the service: real Postgres (Flyway), WireMock as the Gemini API (the default
 * provider) and as the gateway. The LLM is scripted: first it asks for a tool, then it answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgisContainerConfig.class)
class AssistantApiTest {

    private static final WireMockServer llm = start();
    private static final WireMockServer gateway = start();
    private static final UUID CUSTOMER = UUID.randomUUID();

    private static WireMockServer start() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @AfterAll
    static void stop() {
        llm.stop();
        gateway.stop();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("swiftdrop.llm.base-url", llm::baseUrl);
        registry.add("swiftdrop.llm.api-key", () -> "test-key");
        registry.add("swiftdrop.gateway-url", gateway::baseUrl);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    ChatSessionRepository sessions;

    @Autowired
    ChatMessageRepository messages;

    @BeforeEach
    void reset() {
        llm.resetAll();
        gateway.resetAll();
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(CUSTOMER.toString()).tokenValue("customer-token"))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions chat(String body) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/assistant/chat").with(as("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static final String GENERATE = "/v1beta/models/gemini-3.8-flash:generateContent";

    /** The model asks for {@code tool} with {@code args}, then answers {@code answer}. */
    private void scriptLlm(String tool, String args, String answer) {
        llm.stubFor(post(GENERATE).inScenario("turn").whenScenarioStateIs(STARTED)
                .willReturn(okJson("""
                        {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
                          {"functionCall":{"id":"call_1","name":"%s","args":%s},"thoughtSignature":"sig-abc"}]}}]}"""
                        .formatted(tool, args)))
                .willSetStateTo("answered"));
        llm.stubFor(post(GENERATE).inScenario("turn").whenScenarioStateIs("answered")
                .willReturn(okJson("""
                        {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[
                          {"text":"thinking it over","thought":true},{"text":"%s"}]}}]}""".formatted(answer))));
    }

    private List<JsonNode> llmRequests() throws Exception {
        List<JsonNode> bodies = new ArrayList<>();
        for (LoggedRequest r : llm.findAll(postRequestedFor(urlEqualTo(GENERATE)))) {
            bodies.add(json.readTree(r.getBodyAsString()));
        }
        return bodies;
    }

    @Test
    void chatTracksAParcelThroughTheToolAsTheCustomer() throws Exception {
        scriptLlm("track_parcel", "{\"trackingNo\":\"SD-1001\"}", "Your parcel SD-1001 is out for delivery.");
        gateway.stubFor(get("/api/v1/tracking/SD-1001").withHeader("Authorization", equalTo("Bearer customer-token"))
                .willReturn(okJson("""
                        {"status":"OUT_FOR_DELIVERY","pickupCity":"Colombo","dropoffCity":"Kandy",
                         "recipientName":"Nimal Perera","timeline":[]}""")));

        String body = chat("{\"message\":\"Where is my parcel SD-1001?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Your parcel SD-1001 is out for delivery.")) // thought part not shown
                .andExpect(jsonPath("$.toolsUsed[0]").value("track_parcel"))
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(json.readTree(body).path("sessionId").asText());

        List<JsonNode> calls = llmRequests();
        assertThat(calls).hasSize(2);
        JsonNode first = calls.get(0);
        assertThat(first.path("systemInstruction").path("parts").get(0).path("text").asText()).contains("SwiftDrop");
        JsonNode declarations = first.path("tools").get(0).path("functionDeclarations");
        List<String> names = new ArrayList<>();
        for (JsonNode d : declarations) {
            names.add(d.path("name").asText());
            if (d.path("name").asText().equals("list_my_parcels")) {
                assertThat(d.has("parameters")).isFalse(); // no arguments, no parameters
            } else {
                assertThat(d.path("parameters").path("type").asText()).isEqualTo("OBJECT");
                assertThat(d.path("parameters").has("additionalProperties")).isFalse();
            }
        }
        assertThat(names).containsExactlyInAnyOrder("track_parcel", "quote_fee", "list_my_parcels");
        llm.verify(postRequestedFor(urlEqualTo(GENERATE)).withHeader("x-goog-api-key", equalTo("test-key")));

        // Second call: the model turn is echoed unchanged (thought signature included), then the function response.
        JsonNode contents = calls.get(1).path("contents");
        assertThat(contents).hasSize(3);
        assertThat(contents.get(1).path("role").asText()).isEqualTo("model");
        assertThat(contents.get(1).path("parts").get(0).path("thoughtSignature").asText()).isEqualTo("sig-abc");
        assertThat(contents.get(2).path("role").asText()).isEqualTo("user");
        JsonNode response = contents.get(2).path("parts").get(0).path("functionResponse");
        assertThat(response.path("name").asText()).isEqualTo("track_parcel");
        assertThat(response.path("id").asText()).isEqualTo("call_1");
        assertThat(response.path("response").path("result").asText()).contains("OUT_FOR_DELIVERY").doesNotContain("Nimal");

        assertThat(messages.findAll()).filteredOn(m -> m.getSessionId().equals(sessionId))
                .extracting(ChatMessage::getRole).containsExactly("user", "tool", "assistant");
    }

    @Test
    void followUpSendsTheEarlierTurnAsContext() throws Exception {
        ChatSession session = sessions.save(new ChatSession(CUSTOMER));
        messages.saveAll(List.of(
                new ChatMessage(session.getId(), ChatMessage.ASSISTANT, "orphan reply", null),
                new ChatMessage(session.getId(), ChatMessage.USER, "How much to Kandy?", null),
                new ChatMessage(session.getId(), ChatMessage.TOOL, "{\"feeLkr\":530}", "quote_fee"),
                new ChatMessage(session.getId(), ChatMessage.ASSISTANT, "It is LKR 530.", null)));
        llm.stubFor(post(GENERATE).willReturn(okJson("""
                {"candidates":[{"finishReason":"STOP","content":{"role":"model","parts":[{"text":"Yes, 2 kg is LKR 530."}]}}]}""")));

        chat("{\"sessionId\":\"" + session.getId() + "\",\"message\":\"And for 2 kg?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(session.getId().toString()));

        JsonNode sent = llmRequests().get(0).path("contents");
        assertThat(sent).hasSize(3); // leading reply dropped
        assertThat(sent.get(0).path("role").asText()).isEqualTo("user");
        assertThat(sent.get(0).path("parts").get(0).path("text").asText()).isEqualTo("How much to Kandy?");
        assertThat(sent.get(1).path("role").asText()).isEqualTo("model");
        assertThat(sent.get(1).path("parts").get(0).path("text").asText()).isEqualTo("It is LKR 530.");
        assertThat(sent.get(2).path("role").asText()).isEqualTo("user");
        assertThat(sent.get(2).path("parts").get(0).path("text").asText()).isEqualTo("And for 2 kg?");
    }

    @Test
    void anotherCustomersParcelGetsARefusalAndNoData() throws Exception {
        scriptLlm("track_parcel", "{\"trackingNo\":\"SD-9999\"}", "I can't find that parcel on your account.");
        gateway.stubFor(get("/api/v1/tracking/SD-9999").willReturn(aResponse().withStatus(403)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":\"DELIVERED\",\"detail\":\"belongs to customer 42\"}")));

        chat("{\"message\":\"Where is SD-9999?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("I can't find that parcel on your account."));

        JsonNode second = llmRequests().get(1);
        JsonNode response = second.path("contents").get(2).path("parts").get(0).path("functionResponse").path("response");
        assertThat(response.path("result").asText()).isEqualTo("I can't find that parcel on your account.");
        assertThat(second.toString()).doesNotContain("DELIVERED", "customer 42");
    }

    @Test
    void providerFailureGivesTheFriendlyReplyNot500() throws Exception {
        llm.stubFor(post(GENERATE).willReturn(aResponse().withStatus(429)
                .withHeader("Content-Type", "application/json").withBody("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}")));

        chat("{\"message\":\"Where is my parcel?\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(ChatService.FALLBACK_REPLY))
                .andExpect(jsonPath("$.sessionId").isNotEmpty());
    }

    @Test
    void modelRefusalGivesAPoliteNo() throws Exception {
        llm.stubFor(post(GENERATE).willReturn(okJson("{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}")));

        chat("{\"message\":\"something off-topic\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(ChatService.REFUSAL_REPLY));
    }

    @Test
    void blockedPromptGivesAPoliteNo() throws Exception {
        llm.stubFor(post(GENERATE).willReturn(okJson("{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")));

        chat("{\"message\":\"something off-topic\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value(ChatService.REFUSAL_REPLY));
    }

    @Test
    void anotherCustomersSessionIs404() throws Exception {
        ChatSession theirs = sessions.save(new ChatSession(UUID.randomUUID()));

        chat("{\"sessionId\":\"" + theirs.getId() + "\",\"message\":\"hi\"}").andExpect(status().isNotFound());
        llm.verify(0, postRequestedFor(urlEqualTo(GENERATE)));
    }

    @Test
    void chatIsForCustomersOnlyAndValidatesTheMessage() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/assistant/chat").contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/assistant/chat").with(as("DISPATCHER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isForbidden());
        chat("{\"message\":\"  \"}").andExpect(status().isBadRequest());
        chat("{\"message\":\"" + "x".repeat(2001) + "\"}").andExpect(status().isBadRequest());
    }

    // ---------- MCP ----------

    private ResultActions mcp(String body, String role) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/assistant/mcp").with(as(role))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void mcpInitializeAndListTools() throws Exception {
        mcp("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-06-18\"}}", "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.result.protocolVersion").value("2025-06-18"))
                .andExpect(jsonPath("$.result.capabilities.tools").exists());
        mcp("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "CUSTOMER").andExpect(status().isAccepted());
        mcp("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}", "CUSTOMER")
                .andExpect(jsonPath("$.result.tools.length()").value(3))
                .andExpect(jsonPath("$.result.tools[?(@.name=='quote_fee')].inputSchema.required[0]").value("pickupCity"));
        mcp("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}", "CUSTOMER").andExpect(jsonPath("$.result").isMap());
    }

    @Test
    void mcpToolCallRunsAsTheCallingCustomer() throws Exception {
        gateway.stubFor(get("/api/v1/parcels/mine").withHeader("Authorization", equalTo("Bearer customer-token"))
                .willReturn(okJson("[{\"trackingNo\":\"SD-1001\",\"currentStatus\":\"IN_TRANSIT\",\"recipientName\":\"Nimal\"}]")));

        mcp("{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"list_my_parcels\",\"arguments\":{}}}", "CUSTOMER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].type").value("text"))
                .andExpect(jsonPath("$.result.content[0].text").value("[{\"trackingNo\":\"SD-1001\",\"status\":\"IN_TRANSIT\"}]"));
        gateway.verify(1, getRequestedFor(urlEqualTo("/api/v1/parcels/mine")));
    }

    @Test
    void mcpErrorsAndAccess() throws Exception {
        mcp("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"drop_tables\"}}", "CUSTOMER")
                .andExpect(jsonPath("$.error.code").value(-32602));
        mcp("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"resources/list\"}", "CUSTOMER")
                .andExpect(jsonPath("$.error.code").value(-32601));
        mcp("{\"jsonrpc\":\"2.0\",\"id\":10,\"method\":\"tools/list\"}", "MERCHANT").andExpect(status().isForbidden());
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/assistant/mcp").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"tools/list\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/v1/assistant/mcp")
                .with(as("CUSTOMER"))).andExpect(status().isMethodNotAllowed());
    }
}
