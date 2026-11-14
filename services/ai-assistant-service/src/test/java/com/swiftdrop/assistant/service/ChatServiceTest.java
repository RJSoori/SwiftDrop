package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.assistant.domain.ChatMessage;
import com.swiftdrop.assistant.domain.ChatSession;
import com.swiftdrop.assistant.repository.ChatMessageRepository;
import com.swiftdrop.assistant.repository.ChatSessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Without LLM_API_KEY the assistant answers with the fixed reply and never calls the model or the tools. */
class ChatServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void noApiKeyGivesTheFixedReplyAndStoresTheTurn() {
        ChatSessionRepository sessions = mock(ChatSessionRepository.class);
        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        when(sessions.save(any())).thenAnswer(i -> i.getArgument(0));
        ObjectMapper json = new ObjectMapper();
        // Unreachable URLs: any call to the model or the gateway would fail the test.
        LlmClient llm = new GeminiClient(RestClient.builder(), json, "http://127.0.0.1:1", "", GeminiClient.DEFAULT_MODEL);
        ParcelTools tools = new ParcelTools(RestClient.builder(), "http://127.0.0.1:1", json);
        ChatService service = new ChatService(sessions, messages, llm, tools);

        ChatService.Reply reply = service.chat(UUID.randomUUID(), "customer-token", null, "Where is SD-1001?");

        assertThat(llm.configured()).isFalse();
        assertThat(reply.reply()).isEqualTo(ChatService.FALLBACK_REPLY);
        assertThat(reply.toolsUsed()).isEmpty();
        assertThat(reply.sessionId()).isNotNull();
        ArgumentCaptor<List<ChatMessage>> saved = ArgumentCaptor.forClass(List.class);
        verify(messages).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(ChatMessage::getRole).containsExactly("user", "assistant");
        verify(sessions).save(any(ChatSession.class));
    }
}
