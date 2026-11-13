package com.swiftdrop.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Merchant integrations call /api/v1/merchant/orders/** with an X-API-Key instead of a login. The key is
 * introspected by auth-service, which returns a short-lived merchant JWT; that JWT replaces the Authorization
 * header and the normal JWT checks run on it. Requests without the header are untouched.
 * Results are cached in Redis for 60 s under the key's SHA-256 hash; the raw key is never stored or forwarded.
 */
@Component
public class MerchantApiKeyFilter implements WebFilter, Ordered {

    static final String HEADER = "X-API-Key";
    static final String CACHE_PREFIX = "gw:api-key:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final ServerWebExchangeMatcher ORDERS = ServerWebExchangeMatchers.pathMatchers("/api/v1/merchant/orders/**");

    record Introspection(String merchantId, String status, String token) {
    }

    private static final class InvalidKey extends RuntimeException {
        InvalidKey() {
            super(null, null, false, false);
        }
    }

    private final WebClient auth;
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper json;

    public MerchantApiKeyFilter(WebClient.Builder builder, ReactiveStringRedisTemplate redis, ObjectMapper json,
                                @Value("${swiftdrop.routes.auth}") String authUrl,
                                @Value("${swiftdrop.internal-token}") String internalToken) {
        this.auth = builder.clone().baseUrl(authUrl).defaultHeader("X-Internal-Token", internalToken).build();
        this.redis = redis;
        this.json = json;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (apiKey == null) {
            return chain.filter(exchange);
        }
        return ORDERS.matches(exchange).flatMap(m -> m.isMatch() ? withApiKey(exchange, chain, apiKey) : chain.filter(exchange));
    }

    private Mono<Void> withApiKey(ServerWebExchange exchange, WebFilterChain chain, String apiKey) {
        String cacheKey = CACHE_PREFIX + sha256(apiKey);
        Mono<Introspection> lookup = redis.opsForValue().get(cacheKey)
                .onErrorResume(e -> Mono.empty()) // Redis down: ask auth-service every time
                .map(this::fromJson)
                .switchIfEmpty(Mono.defer(() -> introspect(apiKey).flatMap(i -> cache(cacheKey, i).thenReturn(i))));

        return lookup.map(i -> new Lookup(i, null))
                .onErrorResume(InvalidKey.class, e -> Mono.just(new Lookup(null, HttpStatus.UNAUTHORIZED)))
                .onErrorResume(e -> Mono.just(new Lookup(null, HttpStatus.SERVICE_UNAVAILABLE)))
                .flatMap(l -> {
                    if (l.error == HttpStatus.UNAUTHORIZED) {
                        return Problems.write(exchange, l.error, "Invalid API key");
                    }
                    if (l.error != null) {
                        return Problems.write(exchange, l.error, "API key check unavailable, try again");
                    }
                    // Only APPROVED merchants pass: PENDING, SUSPENDED and REJECTED are all refused.
                    if (!"APPROVED".equals(l.result.status())) {
                        return Problems.write(exchange, HttpStatus.FORBIDDEN, "Merchant account is not active");
                    }
                    ServerWebExchange authed = exchange.mutate().request(r -> r.headers(h -> {
                        h.remove(HEADER);
                        h.set(HttpHeaders.AUTHORIZATION, "Bearer " + l.result.token());
                    })).build();
                    return chain.filter(authed);
                });
    }

    private record Lookup(Introspection result, HttpStatus error) {
    }

    private Mono<Introspection> introspect(String apiKey) {
        return auth.post().uri("/internal/v1/api-keys/introspect")
                .bodyValue(Map.of("apiKey", apiKey))
                .retrieve()
                .onStatus(s -> s.value() == 401, r -> Mono.error(new InvalidKey()))
                .bodyToMono(Introspection.class);
    }

    private Mono<Boolean> cache(String cacheKey, Introspection result) {
        try {
            return redis.opsForValue().set(cacheKey, json.writeValueAsString(result), CACHE_TTL)
                    .onErrorReturn(false);
        } catch (JsonProcessingException e) {
            return Mono.just(false);
        }
    }

    private Introspection fromJson(String value) {
        try {
            return json.readValue(value, Introspection.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt API key cache entry", e);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** After the request id, before Spring Security (-100), so security sees the swapped-in JWT. */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
