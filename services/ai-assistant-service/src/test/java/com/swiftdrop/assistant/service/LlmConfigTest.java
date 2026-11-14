package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** LLM_PROVIDER picks the client; a blank LLM_MODEL means the provider's default. */
class LlmConfigTest {

    private final ObjectMapper json = new ObjectMapper();

    private LlmClient create(String provider, String model) {
        return LlmConfig.create(RestClient.builder(), json, provider, "key", model, "");
    }

    @Test
    void geminiIsTheDefaultWithAFreeTierModel() {
        LlmClient llm = create("gemini", "");
        assertThat(llm).isInstanceOf(GeminiClient.class);
        assertThat(((GeminiClient) llm).model()).isEqualTo("gemini-3.8-flash");
        assertThat(llm.configured()).isTrue();
    }

    @Test
    void anthropicAndAnExplicitModel() {
        assertThat(((AnthropicClient) create(" Anthropic ", "")).model()).isEqualTo(AnthropicClient.DEFAULT_MODEL);
        assertThat(((GeminiClient) create("GEMINI", "gemini-other")).model()).isEqualTo("gemini-other");
    }

    @Test
    void unknownProviderFailsAtStartup() {
        assertThatThrownBy(() -> create("openai", "")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini or anthropic");
    }
}
