package com.swiftdrop.dispatch.courier;

import com.swiftdrop.dispatch.repository.AssignmentRepository;
import com.swiftdrop.common.web.InternalTokenInterceptor;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClient;

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
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "assignment-lookup", havingValue = "stub")
    AssignmentLookup assignmentLookup() {
        return new StubAssignmentLookup();
    }

    @Bean
    @ConditionalOnMissingBean(AssignmentLookup.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "assignment-lookup",
            havingValue = "database", matchIfMissing = true)
    AssignmentLookup repositoryAssignmentLookup(AssignmentRepository assignmentRepository, Clock clock) {
        return new RepositoryAssignmentLookup(assignmentRepository, clock);
    }

    @Bean
    @ConditionalOnMissingBean(ParcelClient.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "parcel-client", havingValue = "stub")
    ParcelClient parcelClient() {
        return new StubParcelClient();
    }

    @Bean
    @ConditionalOnMissingBean(ParcelClient.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "parcel-client",
            havingValue = "http", matchIfMissing = true)
    ParcelClient httpParcelClient(@Value("${swiftdrop.courier.parcel-service-url}") String baseUrl,
                                  @Value("${INTERNAL_TOKEN:}") String internalToken) {
        return new HttpParcelClient(baseUrl, internalToken);
    }

    @Bean
    @ConditionalOnMissingBean(NotificationClient.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "notification-client", havingValue = "stub")
    NotificationClient notificationClient() {
        return new StubNotificationClient();
    }

    @Bean
    @ConditionalOnMissingBean(NotificationClient.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "notification-client",
            havingValue = "http", matchIfMissing = true)
    NotificationClient httpNotificationClient(@Value("${swiftdrop.courier.notification-service-url}") String baseUrl,
                                              @Value("${INTERNAL_TOKEN:}") String internalToken) {
        return new HttpNotificationClient(baseUrl, internalToken);
    }

    @Bean
    @ConditionalOnMissingBean(ProofStorage.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "proof-storage", havingValue = "stub")
    ProofStorage proofStorage(@Value("${swiftdrop.courier.proof-bucket}") String bucket) {
        return new StubProofStorage(bucket);
    }

    @Bean
    @ConditionalOnMissingBean(ProofStorage.class)
    @ConditionalOnProperty(prefix = "swiftdrop.courier", name = "proof-storage",
            havingValue = "minio", matchIfMissing = true)
    ProofStorage minioProofStorage(@Value("${MINIO_ENDPOINT:http://localhost:9000}") String endpoint,
                                   @Value("${MINIO_ACCESS_KEY:}") String accessKey,
                                   @Value("${MINIO_SECRET_KEY:}") String secretKey,
                                   CourierProperties properties) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        return new MinioProofStorage(client, properties.proofBucket());
    }
}
