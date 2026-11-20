package com.swiftdrop.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/**
 * A real Redis for {@code @SpringBootTest} (idempotency keys in merchant-service, pub/sub in realtime-service).
 * {@code @ServiceConnection(name = "redis")} sets spring.data.redis.* for you.
 * <p>
 * Usage: {@code @Import(RedisContainerConfig.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RedisContainerConfig {

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(TestImages.REDIS).withExposedPorts(6379);
    }
}
