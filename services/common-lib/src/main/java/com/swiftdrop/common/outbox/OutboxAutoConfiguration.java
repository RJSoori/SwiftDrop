package com.swiftdrop.common.outbox;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Shared transactional outbox. Opt in with {@code swiftdrop.outbox.enabled=true}; the service must also create
 * an {@code outbox_events} table in its own schema (see {@link OutboxEvent} for the columns).
 * {@link AutoConfigurationPackage} adds this package to the service's entity and repository scan, so the
 * service's own entities and repositories are still found as usual.
 */
@AutoConfiguration(before = {HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
@ConditionalOnClass({JpaRepository.class, RabbitTemplate.class})
@ConditionalOnProperty(prefix = "swiftdrop.outbox", name = "enabled", havingValue = "true")
@AutoConfigurationPackage(basePackageClasses = OutboxEvent.class)
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(OutboxEventRepository outboxEventRepository,
                                   ObjectProvider<RabbitTemplate> rabbitTemplateProvider) {
        return new OutboxRelay(outboxEventRepository, rabbitTemplateProvider);
    }
}
