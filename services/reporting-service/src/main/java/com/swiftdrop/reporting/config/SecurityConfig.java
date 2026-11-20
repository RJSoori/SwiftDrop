package com.swiftdrop.reporting.config;

import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * The common-lib JWT setup, plus the Swagger UI (reachable only inside the compose network; the gateway does not
 * route it). Report endpoints are DISPATCHER or ADMIN, checked here on the URL and again on each method.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                                   BearerTokenResolver tokenResolver,
                                                   ProblemDetailAuthHandler authHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/reports/**").hasAnyRole("DISPATCHER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(authHandler).accessDeniedHandler(authHandler));
        return http.build();
    }
}
