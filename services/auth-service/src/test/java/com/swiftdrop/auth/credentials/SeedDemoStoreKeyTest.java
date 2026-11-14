package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.config.SeedDataRunner;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.SecretTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DEMO_STORE_API_KEY (application-test.yml) is seeded as a hashed API key of the approved demo merchant. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class SeedDemoStoreKeyTest {

    private static final String DEMO_KEY = "sd_live_test-demo-store-key-not-a-real-secret-0000";

    @Autowired
    SeedDataRunner seedDataRunner;
    @Autowired
    CredentialService credentialService;
    @Autowired
    ApiCredentialRepository credentials;
    @Autowired
    UserRepository users;
    @Autowired
    MerchantAccountRepository merchants;

    private MerchantAccount demoMerchant() {
        return merchants.findByUserId(users.findByEmail("merchant@swiftdrop.test").orElseThrow().getId()).orElseThrow();
    }

    @Test
    void seededKeyIsStoredAsHashAndWorksAtIntrospection() {
        ApiCredential stored = credentials.findBySecretHashAndType(SecretTokens.sha256(DEMO_KEY), ApiCredentialType.API_KEY)
                .orElseThrow();
        assertThat(stored.getName()).isEqualTo("Demo Store");
        assertThat(stored.getKeyPrefix()).isEqualTo(DEMO_KEY.substring(0, 14)).doesNotContain(DEMO_KEY);

        CredentialService.Introspection result = credentialService.introspect(DEMO_KEY);
        assertThat(result.status()).isEqualTo("APPROVED");
        assertThat(result.merchantId()).isEqualTo(demoMerchant().getUser().getId());
        assertThat(result.token()).isNotBlank();
    }

    @Test
    void restartingDoesNotDuplicateTheKey() {
        seedDataRunner.run(null);
        seedDataRunner.run(null);
        long seeded = credentials.findByMerchant_IdOrderByCreatedAtDesc(demoMerchant().getId()).stream()
                .filter(c -> c.getSecretHash().equals(SecretTokens.sha256(DEMO_KEY)))
                .count();
        assertThat(seeded).isEqualTo(1);
        assertThat(credentialService.registerSeedApiKey(demoMerchant(), "Demo Store", DEMO_KEY)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-a-key", "sd_live_tooshort", "sd_test_0123456789012345678901234567890123"})
    void malformedSeedKeysAreRefused(String key) {
        assertThatThrownBy(() -> credentialService.registerSeedApiKey(demoMerchant(), "Demo Store", key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining(key.isEmpty() ? "\u0000" : key);
    }
}
