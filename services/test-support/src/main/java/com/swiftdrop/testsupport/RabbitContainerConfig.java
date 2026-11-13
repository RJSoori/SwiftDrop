package com.swiftdrop.testsupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * A real RabbitMQ broker for {@code @SpringBootTest}. {@code @ServiceConnection} sets spring.rabbitmq.* for you.
 * Needed for the event-flow tests (publish an event, check the consumer did its job).
 * <p>
 * Usage: {@code @Import(RabbitContainerConfig.class)}, usually together with {@link PostgisContainerConfig}.
 * Publish events with the {@link TestEvents} bean this config also registers.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RabbitContainerConfig {

    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(TestImages.RABBITMQ);
    }

    /** Uses the service's own ObjectMapper when it has one, so events serialise exactly as in production. */
    @Bean
    public TestEvents testEvents(RabbitTemplate rabbitTemplate, ObjectProvider<ObjectMapper> objectMapper) {
        return new TestEvents(rabbitTemplate, objectMapper.getIfAvailable(() -> JsonMapper.builder().findAndAddModules().build()));
    }
}
