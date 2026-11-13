package com.swiftdrop.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * A real Postgres 16 + PostGIS database for {@code @SpringBootTest}. {@code @ServiceConnection} points the datasource
 * (and Flyway) at it, so your service's own migrations run exactly as in compose.
 * <p>
 * Usage: {@code @Import(PostgisContainerConfig.class)} on the test class. Every test class with the same
 * configuration shares one cached Spring context, and with it one container.
 * <p>
 * The PostGIS extension is already installed in the {@code public} schema, so {@code ST_Contains},
 * {@code geometry(Polygon, 4326)} and friends work from any service schema.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgisContainerConfig {

    @Bean
    @ServiceConnection
    public PostgreSQLContainer<?> postgisContainer() {
        return new PostgreSQLContainer<>(TestImages.POSTGIS);
    }
}
