package com.swiftdrop.assistant.repository;

import com.swiftdrop.assistant.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    /** Scoped to the caller, so one customer can never continue another customer's session. */
    Optional<ChatSession> findByIdAndUserId(UUID id, UUID userId);
}
