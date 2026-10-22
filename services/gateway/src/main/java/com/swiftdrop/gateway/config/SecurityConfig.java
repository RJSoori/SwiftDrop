package com.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Validates the JWT (Authorization header or sd_access cookie) on every route except the public auth endpoints,
 * and applies coarse role rules per route prefix. Services re-check with @PreAuthorize.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    static final ServerWebExchangeMatcher PUBLIC_ENDPOINTS = new OrServerWebExchangeMatcher(
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.POST,
                    "/api/v1/auth/signup", "/api/v1/auth/signup/merchant", "/api/v1/auth/login", "/api/v1/auth/refresh"),
            ServerWebExchangeMatchers.pathMatchers(HttpMethod.GET, "/api/v1/auth/oauth2/**"),
            ServerWebExchangeMatchers.pathMatchers("/.well-known/**", "/actuator/health", "/actuator/health/**"));

    private static final String ACCESS_COOKIE = "sd_access";

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(Customizer.withDefaults())
                .authorizeExchange(a -> a
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .matchers(PUBLIC_ENDPOINTS).permitAll()
                        .pathMatchers("/api/v1/auth/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/dispatch/**").hasAnyRole("DISPATCHER", "ADMIN")
                        .pathMatchers("/api/v1/merchant/**").hasRole("MERCHANT")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(o -> o
                        .bearerTokenConverter(headerOrCookieToken())
                        .jwt(j -> j.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(rolesConverter()))))
                .build();
    }

    /** Header first, then the sd_access cookie. Public endpoints ignore tokens so a stale cookie cannot cause a 401. */
    private static ServerAuthenticationConverter headerOrCookieToken() {
        ServerBearerTokenAuthenticationConverter header = new ServerBearerTokenAuthenticationConverter();
        return exchange -> PUBLIC_ENDPOINTS.matches(exchange).flatMap(match -> {
            if (match.isMatch()) {
                return Mono.empty();
            }
            return header.convert(exchange).switchIfEmpty(Mono.defer(() -> {
                HttpCookie cookie = exchange.getRequest().getCookies().getFirst(ACCESS_COOKIE);
                return cookie == null || cookie.getValue().isBlank()
                        ? Mono.empty()
                        : Mono.just(new BearerTokenAuthenticationToken(cookie.getValue()));
            }));
        });
    }

    private static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${swiftdrop.cors-allowed-origins}") List<String> origins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setExposedHeaders(List.of(RequestIdFilter.HEADER));
        cors.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
