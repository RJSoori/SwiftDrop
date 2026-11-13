package com.swiftdrop.auth.admin;

import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Service-to-service only (X-Internal-Token). notification-service looks up a customer's email here at send time,
 * because events never carry personal data. Returns only what a notification needs.
 */
@RestController
@RequestMapping("/internal/v1/users")
public class InternalUserController {

    public record UserContact(UUID id, String email, String fullName) {
    }

    private final UserRepository users;

    public InternalUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/{id}/contact")
    @Transactional(readOnly = true)
    public UserContact contact(@PathVariable UUID id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserContact(user.getId(), user.getEmail(), user.getFullName());
    }
}
