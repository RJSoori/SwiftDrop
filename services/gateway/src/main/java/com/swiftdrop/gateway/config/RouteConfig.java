package com.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, KeyResolver clientIpKeyResolver,
                               RedisRateLimiter authRateLimiter, RedisRateLimiter trackingRateLimiter,
                               @Value("${swiftdrop.routes.auth}") String auth,
                               @Value("${swiftdrop.routes.parcel}") String parcel,
                               @Value("${swiftdrop.routes.dispatch}") String dispatch,
                               @Value("${swiftdrop.routes.merchant}") String merchant,
                               @Value("${swiftdrop.routes.notification}") String notification,
                               @Value("${swiftdrop.routes.realtime}") String realtime,
                               @Value("${swiftdrop.routes.reporting}") String reporting,
                               @Value("${swiftdrop.routes.assistant}") String assistant) {
        return builder.routes()
                // Checked first: credential endpoints get a per-IP rate limit.
                .route("auth-rate-limited", r -> r.order(-1)
                        .path("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/signup/**", "/api/v1/auth/refresh")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(authRateLimiter).setKeyResolver(clientIpKeyResolver)))
                        .uri(auth))
                .route("auth", r -> r.path("/api/v1/auth/**", "/.well-known/**").uri(auth))
                .route("parcels", r -> r.path("/api/v1/parcels/**", "/api/v1/quotes/**", "/api/v1/zones/**",
                        "/api/v1/admin/zones/**").uri(parcel))
                .route("dispatch", r -> r.path("/api/v1/dispatch/**", "/api/v1/courier/**").uri(dispatch))
                .route("merchant", r -> r.path("/api/v1/merchant/**").uri(merchant))
                .route("notification", r -> r.path("/api/v1/notifications/**").uri(notification))
                // Public: per-IP limit. Netty flushes text/event-stream chunk by chunk, so /stream (SSE) is not buffered.
                .route("tracking", r -> r.path("/api/v1/tracking/**")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(trackingRateLimiter).setKeyResolver(clientIpKeyResolver)))
                        .uri(realtime))
                .route("realtime-ws", r -> r.path("/ws/**").uri(realtime.replaceFirst("^http", "ws")))
                .route("reporting", r -> r.path("/api/v1/reports/**").uri(reporting))
                .route("assistant", r -> r.path("/api/v1/assistant/**").uri(assistant))
                .build();
    }

    /** 10 requests per minute per key: each request costs 6 tokens, 1 token refills per second, bucket holds 60. */
    @Bean
    @Primary // the default limiter the gateway's filter factory is built with
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(1, 60, 6);
    }

    /** 60 requests per minute per IP (bursts up to 60). Buckets are per route, so tracking never eats the auth budget. */
    @Bean
    public RedisRateLimiter trackingRateLimiter() {
        return new RedisRateLimiter(1, 60, 1);
    }

    /** /internal/** is service-to-service only: 404 at the edge, before security, whatever token is sent. */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 1)
    public WebFilter internalPathBlocker() {
        ServerWebExchangeMatcher internal = ServerWebExchangeMatchers.pathMatchers("/internal", "/internal/**");
        return (exchange, chain) -> internal.matches(exchange).flatMap(m -> m.isMatch()
                ? Problems.write(exchange, HttpStatus.NOT_FOUND, "Not found")
                : chain.filter(exchange));
    }

    /**
     * Client IP: the last X-Forwarded-For hop (appended by the edge proxy / ingress; Next.js passes it through
     * unchanged), else the socket address. Locally there is no edge proxy, so browser traffic arriving via the
     * web container shares one bucket - the same as it would anyway behind Docker's NAT.
     */
    // ponytail: trusts X-Forwarded-For from anyone who can reach :8080; restrict gateway exposure to the proxy in production.
    @Bean
    public KeyResolver clientIpKeyResolver() {
        return exchange -> {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                return Mono.just(hops[hops.length - 1].trim());
            }
            InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
            return Mono.just(remote == null ? "unknown" : remote.getAddress().getHostAddress());
        };
    }
}
