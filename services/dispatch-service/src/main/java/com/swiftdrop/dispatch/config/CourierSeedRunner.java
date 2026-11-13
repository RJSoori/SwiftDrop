package com.swiftdrop.dispatch.config;

import com.swiftdrop.common.web.InternalTokenInterceptor;
import com.swiftdrop.dispatch.domain.Courier;
import com.swiftdrop.dispatch.repository.CourierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "swiftdrop.seed.enabled", havingValue = "true")
public class CourierSeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(CourierSeedRunner.class);
    private static final List<SeedCourier> SEED_COURIERS = List.of(
            new SeedCourier("courier1@swiftdrop.test", "Chamara Courier", "MOTORBIKE"),
            new SeedCourier("courier2@swiftdrop.test", "Kasun Courier", "MOTORBIKE"));

    private final CourierRepository couriers;
    private final RestClient auth;

    public CourierSeedRunner(CourierRepository couriers,
                              RestClient.Builder restClientBuilder,
                              @Value("${AUTH_SERVICE_URL:http://localhost:8081}") String authUrl,
                              @Value("${INTERNAL_TOKEN:}") String internalToken) {
        this.couriers = couriers;
        this.auth = restClientBuilder
                .baseUrl(authUrl)
                .requestInterceptor(new InternalTokenInterceptor(internalToken))
                .build();
    }

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) {
        for (SeedCourier seed : SEED_COURIERS) {
            AuthUser user = auth.get()
                    .uri(uri -> uri.path("/internal/users/by-email").queryParam("email", seed.email()).build())
                    .retrieve()
                    .body(AuthUser.class);
            if (user == null || user.id() == null) {
                throw new IllegalStateException("Auth service returned no user for " + seed.email());
            }
            if (!couriers.existsById(user.id())) {
                couriers.save(new Courier(user.id(), user.name() == null ? seed.fallbackName() : user.name(),
                        null, seed.vehicleType()));
                log.info("Seeded dispatch courier profile for {}", seed.email());
            }
        }
    }

    private record SeedCourier(String email, String fallbackName, String vehicleType) {
    }

    private record AuthUser(UUID id, String name) {
    }
}
