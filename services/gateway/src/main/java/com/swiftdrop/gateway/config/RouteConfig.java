package com.swiftdrop.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder, RedisRateLimiter authRateLimiter, KeyResolver clientIpKeyResolver,
                               @Value("${swiftdrop.routes.auth}") String auth,
                               @Value("${swiftdrop.routes.parcel}") String parcel,
                               @Value("${swiftdrop.routes.dispatch}") String dispatch,
                               @Value("${swiftdrop.routes.merchant}") String merchant) {
        return builder.routes()
                // Checked first: credential endpoints get a per-IP rate limit.
                .route("auth-rate-limited", r -> r.order(-1)
                        .path("/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/signup/**", "/api/v1/auth/refresh")
                        .filters(f -> f.requestRateLimiter(c -> c.setRateLimiter(authRateLimiter).setKeyResolver(clientIpKeyResolver)))
                        .uri(auth))
                .route("auth", r -> r.path("/api/v1/auth/**", "/.well-known/**").uri(auth))
                .route("parcels", r -> r.path("/api/v1/parcels/**").uri(parcel))
                .route("dispatch", r -> r.path("/api/v1/dispatch/**").uri(dispatch))
                .route("merchant", r -> r.path("/api/v1/merchant/**").uri(merchant))
                .build();
    }

    /** 10 requests per minute per key: each request costs 6 tokens, 1 token refills per second, bucket holds 60. */
    @Bean
    public RedisRateLimiter authRateLimiter() {
        return new RedisRateLimiter(1, 60, 6);
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
