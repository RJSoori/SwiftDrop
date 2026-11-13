package com.swiftdrop.testsupport;

import org.testcontainers.utility.DockerImageName;

/** The same images (and tags) as infra/compose/docker-compose.yml, so tests run against what the demo runs on. */
public final class TestImages {

    /** Postgres 16 with PostGIS 3.5. Declared compatible so Testcontainers' PostgreSQLContainer accepts it. */
    public static final DockerImageName POSTGIS =
            DockerImageName.parse("postgis/postgis:16-3.5").asCompatibleSubstituteFor("postgres");

    public static final DockerImageName RABBITMQ =
            DockerImageName.parse("rabbitmq:4.1-management").asCompatibleSubstituteFor("rabbitmq");

    public static final DockerImageName REDIS = DockerImageName.parse("redis:7.4-alpine");

    private TestImages() {
    }
}
