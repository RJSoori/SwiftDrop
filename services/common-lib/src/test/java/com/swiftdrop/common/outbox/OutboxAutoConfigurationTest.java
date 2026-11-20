package com.swiftdrop.common.outbox;

import com.swiftdrop.testapp.TestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.verify;

/**
 * Real Postgres: proves a service gets the shared entity, repository and a scheduled relay just by setting the property.
 * The test waits for the scheduler instead of calling the relay itself, so the row is published exactly once.
 */
@SpringBootTest(classes = TestApplication.class, properties = {
        "swiftdrop.outbox.enabled=true",
        "swiftdrop.outbox.poll-interval-ms=200",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks"})
@Import(OutboxAutoConfigurationTest.Postgres.class)
class OutboxAutoConfigurationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Postgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgres() {
            return new PostgreSQLContainer<>("postgres:16-alpine");
        }
    }

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    @Autowired
    OutboxEventRepository repository;

    @Test
    void relaysRowsFromTheServiceOutboxTable() {
        OutboxEvent saved = repository.save(new OutboxEvent(UUID.randomUUID(), "parcel.created", "{\"trackingNo\":\"SD-1\"}"));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(repository.findById(saved.getId())).get()
                        .extracting(OutboxEvent::getPublishedAt).isNotNull());

        verify(rabbitTemplate).convertAndSend("parcel.events", "parcel.created", (Object) "{\"trackingNo\":\"SD-1\"}");
        assertThat(repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()).isEmpty();
    }

    @Test
    void staysOffWithoutTheProperty() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .run(context -> assertThat(context).doesNotHaveBean(OutboxRelay.class));
    }
}
