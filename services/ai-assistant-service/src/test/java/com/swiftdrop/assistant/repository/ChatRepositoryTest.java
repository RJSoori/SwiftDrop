package com.swiftdrop.assistant.repository;

import com.swiftdrop.assistant.domain.ChatMessage;
import com.swiftdrop.assistant.domain.ChatSession;
import com.swiftdrop.testsupport.PostgisContainerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Postgres with the service's Flyway migrations, so the schema and the queries are checked together. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgisContainerConfig.class)
class ChatRepositoryTest {

    @Autowired
    ChatSessionRepository sessions;

    @Autowired
    ChatMessageRepository messages;

    @Test
    void contextWindowIsTheLastTenUserAndAssistantMessagesNewestFirst() {
        ChatSession session = sessions.save(new ChatSession(UUID.randomUUID()));
        ChatSession other = sessions.save(new ChatSession(UUID.randomUUID()));
        List<ChatMessage> rows = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            rows.add(new ChatMessage(session.getId(), ChatMessage.USER, "q" + i, null));
            rows.add(new ChatMessage(session.getId(), ChatMessage.TOOL, "{}", "track_parcel"));
            rows.add(new ChatMessage(session.getId(), ChatMessage.ASSISTANT, "a" + i, null));
        }
        rows.add(new ChatMessage(other.getId(), ChatMessage.USER, "someone else", null));
        messages.saveAll(rows);

        List<ChatMessage> window = messages.findTop10BySessionIdAndRoleInOrderByIdDesc(
                session.getId(), List.of(ChatMessage.USER, ChatMessage.ASSISTANT));

        assertThat(window).extracting(ChatMessage::getContent)
                .containsExactly("a8", "q8", "a7", "q7", "a6", "q6", "a5", "q5", "a4", "q4");
    }

    @Test
    void sessionLookupIsScopedToItsOwner() {
        UUID owner = UUID.randomUUID();
        ChatSession session = sessions.save(new ChatSession(owner));

        assertThat(sessions.findByIdAndUserId(session.getId(), owner)).isPresent();
        assertThat(sessions.findByIdAndUserId(session.getId(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void toolRowsKeepTheirToolName() {
        ChatSession session = sessions.save(new ChatSession(UUID.randomUUID()));
        ChatMessage saved = messages.saveAndFlush(new ChatMessage(session.getId(), ChatMessage.TOOL, "{\"feeLkr\":530}", "quote_fee"));

        assertThat(messages.findById(saved.getId())).get()
                .satisfies(m -> assertThat(m.getToolName()).isEqualTo("quote_fee"));
    }
}
