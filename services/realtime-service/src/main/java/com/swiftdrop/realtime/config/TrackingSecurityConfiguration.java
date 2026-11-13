package com.swiftdrop.realtime.config;

import com.swiftdrop.common.security.InternalTokenAuthorizationManager;
import com.swiftdrop.common.security.ProblemDetailAuthHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class TrackingSecurityConfiguration {

    @Bean
    public SecurityFilterChain realtimeSecurityFilterChain(HttpSecurity http,
                                                           JwtAuthenticationConverter jwtConverter,
                                                           BearerTokenResolver tokenResolver,
                                                           ProblemDetailAuthHandler authHandler,
                                                           InternalTokenAuthorizationManager internalToken) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(InternalTokenAuthorizationManager.PATH_PATTERN).access(internalToken)
                        .requestMatchers(HttpMethod.GET, "/api/v1/tracking/*").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .bearerTokenResolver(tokenResolver)
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authHandler)
                        .accessDeniedHandler(authHandler));
        return http.build();
    }
}
