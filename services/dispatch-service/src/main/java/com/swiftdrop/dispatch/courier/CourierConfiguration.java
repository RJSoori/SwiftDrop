package com.swiftdrop.dispatch.courier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(CourierProperties.class)
public class CourierConfiguration {

    @Bean
    PasswordEncoder courierOtpPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock courierClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(AssignmentLookup.class)
    AssignmentLookup assignmentLookup() {
        return new StubAssignmentLookup();
    }

    @Bean
    @ConditionalOnMissingBean(ParcelClient.class)
    ParcelClient parcelClient() {
        return new StubParcelClient();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationClient.class)
    NotificationClient notificationClient() {
        return new StubNotificationClient();
    }

    @Bean
    @ConditionalOnMissingBean(ProofStorage.class)
    ProofStorage proofStorage(@Value("${swiftdrop.courier.proof-bucket}") String bucket) {
        return new StubProofStorage(bucket);
    }
}
