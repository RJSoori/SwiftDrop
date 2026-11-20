package com.swiftdrop.parcel.zone;

import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

/**
 * ADMIN check on the URL for /api/v1/admin/zones/**, ahead of the service's main chain. Method security alone runs
 * after the request body is bound and validated, so a non-admin would see validation errors instead of a 403.
 */
@Configuration
public class ZoneAdminSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain zoneAdminSecurityFilterChain(HttpSecurity http, JwtAuthenticationConverter jwtConverter,
                                                            BearerTokenResolver tokenResolver,
                                                            ProblemDetailAuthHandler authHandler) throws Exception {
        http.securityMatcher("/api/v1/admin/zones", "/api/v1/admin/zones/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("ADMIN"))
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(tokenResolver)
                        .jwt(j -> j.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .exceptionHandling(e -> e.authenticationEntryPoint(authHandler).accessDeniedHandler(authHandler));
        return http.build();
    }
}
