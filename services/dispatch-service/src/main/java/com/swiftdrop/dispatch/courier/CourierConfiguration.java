package com.swiftdrop.dispatch.courier;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
    @ConditionalOnProperty(name = "swiftdrop.courier.stubs", havingValue = "true", matchIfMissing = true)
    AssignmentLookup assignmentLookup() {
        return new StubAssignmentLookup();
    }

    @Bean
    @ConditionalOnProperty(name = "swiftdrop.courier.stubs", havingValue = "true", matchIfMissing = true)
    ParcelClient parcelClient() {
        return new StubParcelClient();
    }

    @Bean
    @ConditionalOnProperty(name = "swiftdrop.courier.stubs", havingValue = "true", matchIfMissing = true)
    NotificationClient notificationClient() {
        return new StubNotificationClient();
    }

    @Bean
    @ConditionalOnProperty(name = "swiftdrop.courier.stubs", havingValue = "true", matchIfMissing = true)
    ProofStorage proofStorage(@Value("${swiftdrop.courier.proof-bucket}") String bucket) {
        return new StubProofStorage(bucket);
    }
}
