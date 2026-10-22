package com.swiftdrop.auth.config;

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

    private final AuthService authService;
    private final UserRepository users;
    private final MerchantAccountRepository merchants;
    private final String password;

    public SeedDataRunner(AuthService authService, UserRepository users, MerchantAccountRepository merchants,
                          @Value("${swiftdrop.seed.password:}") String password) {
        this.authService = authService;
        this.users = users;
        this.merchants = merchants;
        this.password = password;
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
        User merchant = seed("merchant@swiftdrop.test", "Ruwan Merchant", "0770000006", Role.MERCHANT);
        if (merchant != null) {
            MerchantAccount account = merchants.save(new MerchantAccount(merchant, "Ruwan Stores", "PV-00001"));
            account.approve(null);
        }
        User pending = seed("pending-merchant@swiftdrop.test", "Sanduni Pending", "0770000007", Role.MERCHANT);
        if (pending != null) {
            merchants.save(new MerchantAccount(pending, "Sanduni Crafts", "PV-00002"));
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
