package com.swiftdrop.auth.oauth;

import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.api.AuthCookies;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class GoogleLoginIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EncryptedCookieAuthorizationRequestRepository cookieRepository;
    @Autowired
    GoogleLoginHandlers handlers;
    @Autowired
    UserRepository users;
    @Autowired
    AuthService authService;

    @Test
    void authorizeRedirectsToGoogleWithPkceAndStoresRequestInEncryptedCookie() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth2/authorize/google"))
                .andExpect(status().is3xxRedirection())
                .andExpect(cookie().httpOnly(EncryptedCookieAuthorizationRequestRepository.COOKIE, true))
                .andExpect(cookie().path(EncryptedCookieAuthorizationRequestRepository.COOKIE, "/api/v1/auth/oauth2"))
                .andReturn();

        var location = UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl()).build();
        assertThat(location.getHost()).isEqualTo("accounts.google.com");
        assertThat(location.getQueryParams().getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(location.getQueryParams().getFirst("code_challenge")).isNotBlank();
        assertThat(location.getQueryParams().getFirst("nonce")).isNotBlank();
        assertThat(location.getQueryParams().getFirst("redirect_uri"))
                .isEqualTo("http://localhost:3000/api/v1/auth/oauth2/callback/google");

        // the cookie decrypts back to the same request, including the PKCE verifier (never sent to the browser in clear)
        Cookie stored = result.getResponse().getCookie(EncryptedCookieAuthorizationRequestRepository.COOKIE);
        assertThat(stored.getValue()).doesNotContain("code_verifier");
        MockHttpServletRequest callback = new MockHttpServletRequest();
        callback.setCookies(stored);
        OAuth2AuthorizationRequest loaded = cookieRepository.loadAuthorizationRequest(callback);
        assertThat(loaded.getState()).isEqualTo(java.net.URLDecoder.decode(location.getQueryParams().getFirst("state"), java.nio.charset.StandardCharsets.UTF_8));
        assertThat(loaded.<String>getAttribute("code_verifier")).isNotBlank();
    }

    @Test
    void tamperedCookieIsIgnored() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/auth/oauth2/authorize/google")).andReturn();
        String value = result.getResponse().getCookie(EncryptedCookieAuthorizationRequestRepository.COOKIE).getValue();
        char[] chars = value.toCharArray();
        chars[20] = chars[20] == 'A' ? 'B' : 'A';
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(EncryptedCookieAuthorizationRequestRepository.COOKIE, new String(chars)));

        assertThat(cookieRepository.loadAuthorizationRequest(request)).isNull();
    }

    @Test
    void callbackWithoutStoredRequestFailsBackToLoginPage() throws Exception {
        mvc.perform(get("/api/v1/auth/oauth2/callback/google").param("code", "abc").param("state", "xyz"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/login?error=google"));
    }

    @Test
    void successCreatesCustomerSetsCookiesAndRedirectsToWebApp() throws Exception {
        String email = "g-" + UUID.randomUUID() + "@gmail.com";
        String subject = UUID.randomUUID().toString();

        MockHttpServletResponse first = new MockHttpServletResponse();
        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), first, googleLogin(subject, email, true));

        assertThat(first.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback");
        assertThat(first.getCookie(AuthCookies.ACCESS)).isNotNull();
        assertThat(first.getCookie(AuthCookies.REFRESH)).isNotNull();
        User created = users.findByEmail(email).orElseThrow();
        assertThat(created.getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(created.getPasswordHash()).isNull();

        // same Google account again -> same user, no duplicate
        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(),
                googleLogin(subject, email, true));
        assertThat(users.findByEmail(email).orElseThrow().getId()).isEqualTo(created.getId());
    }

    @Test
    void verifiedGoogleEmailLinksToExistingPasswordAccount() throws Exception {
        String email = "linked-" + UUID.randomUUID() + "@gmail.com";
        User existing = authService.createUser(email, "password1", "Has Password", "0771234567", Role.CUSTOMER);

        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response, googleLogin("sub-" + UUID.randomUUID(), email, true));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/auth/callback");
        assertThat(users.findByEmail(email).orElseThrow().getId()).isEqualTo(existing.getId());
    }

    @Test
    void unverifiedEmailIsRefused() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.onAuthenticationSuccess(new MockHttpServletRequest(), response,
                googleLogin("sub-" + UUID.randomUUID(), "unverified-" + UUID.randomUUID() + "@gmail.com", false));

        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/login?error=google_account");
        assertThat(response.getCookie(AuthCookies.ACCESS)).isNull();
    }

    private static OAuth2AuthenticationToken googleLogin(String subject, String email, boolean verified) {
        OidcIdToken idToken = new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("sub", subject, "email", email, "email_verified", verified, "name", "Google User",
                        "iss", "https://accounts.google.com"));
        DefaultOidcUser user = new DefaultOidcUser(AuthorityUtils.createAuthorityList("OIDC_USER"), idToken);
        return new OAuth2AuthenticationToken(user, user.getAuthorities(), "google");
    }
}
