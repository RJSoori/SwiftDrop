package com.swiftdrop.auth.config;

import com.swiftdrop.common.security.HeaderOrCookieBearerTokenResolver;
import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                                   BearerTokenResolver tokenResolver,
                                                   ProblemDetailAuthHandler authHandler) throws Exception {
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
                .exceptionHandling(e -> e.authenticationEntryPoint(authHandler).accessDeniedHandler(authHandler));
        return http.build();
    }
}
