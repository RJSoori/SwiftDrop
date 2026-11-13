package com.swiftdrop.auth.config;

import com.swiftdrop.auth.credentials.CredentialService;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo accounts, one per role. Runs only when SEED_DEMO_DATA=true; existing accounts are left alone. */
@Component
@ConditionalOnProperty(name = "swiftdrop.seed.enabled", havingValue = "true")
public class SeedDataRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataRunner.class);

    static final String DEMO_MERCHANT_EMAIL = "merchant@swiftdrop.test";
    static final String DEMO_STORE_KEY_NAME = "Demo Store";

    private final AuthService authService;
    private final UserRepository users;
    private final MerchantAccountRepository merchants;
    private final CredentialService credentials;
    private final String password;
    private final String demoStoreApiKey;

    public SeedDataRunner(AuthService authService, UserRepository users, MerchantAccountRepository merchants,
                          CredentialService credentials,
                          @Value("${swiftdrop.seed.password:}") String password,
                          @Value("${swiftdrop.seed.demo-store-api-key:}") String demoStoreApiKey) {
        this.authService = authService;
        this.users = users;
        this.merchants = merchants;
        this.credentials = credentials;
        this.password = password;
        this.demoStoreApiKey = demoStoreApiKey;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("SEED_DEMO_DATA=true but SEED_PASSWORD is empty; skipping demo accounts");
            return;
        }
        seed("admin@swiftdrop.test", "Ayesha Admin", "0770000001", Role.ADMIN);
        seed("dispatcher@swiftdrop.test", "Dinesh Dispatcher", "0770000002", Role.DISPATCHER);
        seed("courier1@swiftdrop.test", "Chamara Courier", "0770000003", Role.COURIER);
        seed("courier2@swiftdrop.test", "Kasun Courier", "0770000004", Role.COURIER);
        seed("customer@swiftdrop.test", "Nimali Customer", "0770000005", Role.CUSTOMER);
        User merchant = seed(DEMO_MERCHANT_EMAIL, "Ruwan Merchant", "0770000006", Role.MERCHANT);
        if (merchant != null) {
            MerchantAccount account = merchants.save(new MerchantAccount(merchant, "Ruwan Stores", "PV-00001"));
            account.approve(null);
        }
        User pending = seed("pending-merchant@swiftdrop.test", "Sanduni Pending", "0770000007", Role.MERCHANT);
        if (pending != null) {
            merchants.save(new MerchantAccount(pending, "Sanduni Crafts", "PV-00002"));
        }
        seedDemoStoreApiKey();
    }

    /**
     * DEMO_STORE_API_KEY (the key the Demo Store sends as X-API-Key) becomes an API key of the approved demo merchant,
     * stored as a hash like any other. Skipped when unset or already registered. The key itself is never logged.
     */
    private void seedDemoStoreApiKey() {
        if (demoStoreApiKey.isBlank()) {
            return;
        }
        MerchantAccount account = users.findByEmail(DEMO_MERCHANT_EMAIL)
                .flatMap(u -> merchants.findByUserId(u.getId()))
                .orElse(null);
        if (account == null) {
            log.warn("DEMO_STORE_API_KEY is set but the demo merchant does not exist; skipping");
            return;
        }
        try {
            if (credentials.registerSeedApiKey(account, DEMO_STORE_KEY_NAME, demoStoreApiKey.trim())) {
                log.info("Seeded the Demo Store API key for the demo merchant");
            }
        } catch (IllegalArgumentException e) {
            log.warn("DEMO_STORE_API_KEY ignored: {}", e.getMessage());
        }
    }

    /** Returns the new user, or null if the email already exists. */
    private User seed(String email, String name, String phone, Role role) {
        if (users.existsByEmail(email)) {
            return null;
        }
        log.info("Seeding demo {} account {}", role, email);
        return authService.createUser(email, password, name, phone, role);
    }
}
