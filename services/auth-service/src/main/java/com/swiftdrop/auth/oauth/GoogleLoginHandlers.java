package com.swiftdrop.auth.oauth;

import com.swiftdrop.auth.api.AuthCookies;
import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.auth.service.GoogleAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

/**
 * After Spring Security has exchanged the code (with PKCE) and verified Google's ID token:
 * find/link/create the user, set SwiftDrop cookies and send the browser to the web app.
 */
@Component
public class GoogleLoginHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandlers.class);

    private final GoogleAccountService googleAccounts;
    private final AuthService authService;
    private final AuthCookies cookies;
    private final AuthProperties props;

    public GoogleLoginHandlers(GoogleAccountService googleAccounts, AuthService authService, AuthCookies cookies,
                               AuthProperties props) {
        this.googleAccounts = googleAccounts;
        this.authService = authService;
        this.cookies = cookies;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OidcUser google = (OidcUser) authentication.getPrincipal();
        try {
            User user = googleAccounts.findOrCreate(google.getSubject(), google.getEmail(),
                    Boolean.TRUE.equals(google.getEmailVerified()), google.getFullName());
            cookies.write(response, authService.startSession(user));
            response.sendRedirect(props.webAppUrl() + "/auth/callback");
        } catch (ResponseStatusException e) {
            log.warn("Google sign-in refused: {}", e.getReason());
            response.sendRedirect(props.webAppUrl() + "/login?error=google_account");
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("Google sign-in failed: {}", exception.getMessage());
        response.sendRedirect(props.webAppUrl() + "/login?error=google");
    }
}
