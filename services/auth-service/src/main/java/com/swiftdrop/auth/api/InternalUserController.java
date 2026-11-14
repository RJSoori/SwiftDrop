package com.swiftdrop.auth.api;

import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
}
