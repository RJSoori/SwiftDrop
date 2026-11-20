package com.swiftdrop.auth.api;

import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/internal/users")
public class InternalUserController {
    private final UserRepository users;

    public InternalUserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/by-email")
    public UserSummary byEmail(@RequestParam String email) {
        User user = users.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserSummary.of(user, null);
    }

    /** Used by notification-service to find the customer's email for a parcel status update. */
    @GetMapping("/{id}")
    public UserSummary byId(@PathVariable UUID id) {
        User user = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserSummary.of(user, null);
    }
}
