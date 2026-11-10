package com.swiftdrop.parcel.config;

import com.swiftdrop.common.security.HeaderOrCookieBearerTokenResolver;
import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final PathPatternRequestMatcher.Builder PATHS = PathPatternRequestMatcher.withDefaults();

    static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATHS.matcher(HttpMethod.POST, "/api/v1/parcels/quote"),
            PATHS.matcher(HttpMethod.GET, "/api/v1/parcels/track/**"),
            PATHS.matcher("/actuator/health"),
            PATHS.matcher("/actuator/health/**"),
            PATHS.matcher("/actuator/info"),
            PATHS.matcher("/error")
    );

    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        HeaderOrCookieBearerTokenResolver delegate = new HeaderOrCookieBearerTokenResolver();
        return request -> PUBLIC_ENDPOINTS.matches(request) ? null : delegate.resolve(request);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthenticationConverter jwtConverter,
                                                   BearerTokenResolver tokenResolver,
                                                   ProblemDetailAuthHandler authHandler) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
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
