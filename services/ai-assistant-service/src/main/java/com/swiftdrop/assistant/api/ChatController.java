package com.swiftdrop.assistant.api;

import com.swiftdrop.assistant.service.ChatService;
import com.swiftdrop.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    public record ChatRequest(UUID sessionId, @NotBlank @Size(max = 2000) String message) {
    }

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    /** Never a 500: if anything below fails (database included) the customer still gets the friendly fixed reply. */
    @PostMapping("/chat")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ChatService.Reply chat(@Valid @RequestBody ChatRequest request) {
        try {
            return chat.chat(CurrentUser.id(), CurrentUser.jwt().getTokenValue(), request.sessionId(), request.message());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Chat turn failed: {}", e.getClass().getSimpleName());
            return new ChatService.Reply(request.sessionId(), ChatService.FALLBACK_REPLY, List.of());
        }
    }
}
