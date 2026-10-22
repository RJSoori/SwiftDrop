package com.swiftdrop.auth.config;

import com.swiftdrop.auth.oauth.EncryptedCookieAuthorizationRequestRepository;
import com.swiftdrop.auth.oauth.GoogleLoginHandlers;
import com.swiftdrop.common.security.HeaderOrCookieBearerTokenResolver;
import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/signup"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/signup/merchant"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/login"),
            PATHS.matcher(HttpMethod.POST, "/api/v1/auth/refresh"),
            PATHS.matcher("/api/v1/auth/oauth2/**"),
            PATHS.matcher("/.well-known/jwks.json"),
            PATHS.matcher("/actuator/health"),
            PATHS.matcher("/actuator/health/**"),
            PATHS.matcher("/error"));

    private static final OAuth2AuthorizedClientRepository DISCARD_AUTHORIZED_CLIENTS = new OAuth2AuthorizedClientRepository() {
        @Override
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id, Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient client, Authentication principal, HttpServletRequest request,
                                         HttpServletResponse response) {
        }

        @Override
        public void removeAuthorizedClient(String id, Authentication principal, HttpServletRequest request,
                                           HttpServletResponse response) {
        }
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Public endpoints ignore tokens, so a stale sd_access cookie cannot turn /login or /refresh into a 401. */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        HeaderOrCookieBearerTokenResolver delegate = new HeaderOrCookieBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : delegate.resolve(request);
    }

    /** Google sign-in starts at /api/v1/auth/oauth2/authorize/google and always uses PKCE (S256). */
    @Bean
    public OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(registrations, "/api/v1/auth/oauth2/authorize");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                                   BearerTokenResolver tokenResolver,
                                                   ProblemDetailAuthHandler authHandler,
                                                   OAuth2AuthorizationRequestResolver pkceResolver,
                                                   EncryptedCookieAuthorizationRequestRepository authRequestCookie,
                                                   GoogleLoginHandlers googleLogin) throws Exception {
        // CSRF is off: stateless API, and the auth cookies are SameSite=Lax so other sites cannot POST with them.
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .oauth2Login(o -> o
                        .authorizationEndpoint(a -> a
                                .authorizationRequestResolver(pkceResolver)
                                .authorizationRequestRepository(authRequestCookie))
                        .redirectionEndpoint(r -> r.baseUri("/api/v1/auth/oauth2/callback/*"))
                        // We issue our own tokens; Google's access token is not needed after login, so do not keep it.
                        .authorizedClientRepository(DISCARD_AUTHORIZED_CLIENTS)
                        .successHandler(googleLogin)
                        .failureHandler(googleLogin))
                .exceptionHandling(e -> e.authenticationEntryPoint(authHandler).accessDeniedHandler(authHandler));
        return http.build();
    }
}
