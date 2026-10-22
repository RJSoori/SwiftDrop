package com.swiftdrop.auth.api;

import com.swiftdrop.auth.api.dto.LoginRequest;
import com.swiftdrop.auth.api.dto.MerchantSignupRequest;
import com.swiftdrop.auth.api.dto.SignupRequest;
import com.swiftdrop.auth.api.dto.UserSummary;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.auth.service.AuthService.AuthSession;
import com.swiftdrop.common.security.CurrentUser;
import com.swiftdrop.common.web.RequestIdFilter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies cookies;

    public AuthController(AuthService authService, AuthCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    /** Customer signup. Logs the new user in straight away. */
    @PostMapping("/signup")
    public ResponseEntity<UserSummary> signup(@Valid @RequestBody SignupRequest request) {
        return withCookies(HttpStatus.CREATED, authService.signup(request));
    }

    /** Merchant signup: MERCHANT role + PENDING merchant account until an admin approves it. */
    @PostMapping("/signup/merchant")
    public ResponseEntity<UserSummary> signupMerchant(@Valid @RequestBody MerchantSignupRequest request) {
        return withCookies(HttpStatus.CREATED, authService.signupMerchant(request));
    }

    @PostMapping("/login")
    public ResponseEntity<UserSummary> login(@Valid @RequestBody LoginRequest request) {
        return withCookies(HttpStatus.OK, authService.login(request));
    }

    /** On failure the cookies are cleared too, so the browser stops presenting a dead session. */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        try {
            return withCookies(HttpStatus.OK, authService.refresh(refreshToken));
        } catch (ResponseStatusException e) {
            ProblemDetail problem = e.getBody();
            problem.setProperty("traceId", RequestIdFilter.current());
            return ResponseEntity.status(e.getStatusCode()).headers(cookies.clear()).body(problem);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().headers(cookies.clear()).build();
    }

    @GetMapping("/me")
    public UserSummary me() {
        return authService.me(CurrentUser.id());
    }

    private ResponseEntity<UserSummary> withCookies(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status).headers(cookies.set(session)).body(session.user());
    }
}
