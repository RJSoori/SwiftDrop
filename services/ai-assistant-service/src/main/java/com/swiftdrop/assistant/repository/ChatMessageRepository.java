package com.swiftdrop.assistant.repository;

import com.swiftdrop.assistant.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Newest first; callers reverse it into chronological order. */
    List<ChatMessage> findTop10BySessionIdAndRoleInOrderByIdDesc(UUID sessionId, Collection<String> roles);
}
