package com.swiftdrop.auth.credentials;

import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.auth.service.SecretTokens;
import com.swiftdrop.auth.service.TokenService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /internal/** needs X-Internal-Token (no JWT), and the introspect endpoint honours the contract that
 * services/gateway MerchantApiKeyFilter relies on: 200 {merchantId, status, token} for a known key, 401 otherwise.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class InternalEndpointsTest {

    private static final String INTERNAL_TOKEN = "test-internal-token";
    private static final String INTROSPECT = "/internal/v1/api-keys/introspect";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository users;
    @Autowired
    MerchantAccountRepository merchants;
    @Autowired
    CredentialService credentialService;
    @Autowired
    ApiCredentialRepository credentials;
    @Autowired
    JwtDecoder jwtDecoder;

    private User newUser(Role role) {
        return authService.createUser("user-" + UUID.randomUUID() + "@example.com", "password1", "Ruwan Perera",
                "0771234567", role);
    }

    private MerchantAccount newMerchant(boolean approved) {
        MerchantAccount merchant = new MerchantAccount(newUser(Role.MERCHANT), "Ruwan Stores", "PV-" + UUID.randomUUID());
        if (approved) {
            merchant.approve(null);
        }
        return merchants.save(merchant);
    }

    private ResultActions introspect(String apiKey) throws Exception {
        return mvc.perform(post(INTROSPECT).header("X-Internal-Token", INTERNAL_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"%s\"}".formatted(apiKey)));
    }

    // ---------- internal-token guard ----------

    @Test
    void internalEndpointsRejectMissingOrWrongToken() throws Exception {
        UUID id = newUser(Role.CUSTOMER).getId();
        mvc.perform(get("/internal/v1/users/{id}/contact", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/users/{id}/contact", id).header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(INTROSPECT).contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"sd_live_x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userJwtIsNotEnoughForInternalEndpoints() throws Exception {
        var admin = jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(get("/internal/v1/users/{id}/contact", UUID.randomUUID()).with(admin))
                .andExpect(status().isForbidden());
    }

    @Test
    void strayAuthorizationHeaderDoesNotBreakAValidInternalCall() throws Exception {
        User user = newUser(Role.CUSTOMER);
        mvc.perform(get("/internal/v1/users/{id}/contact", user.getId())
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isOk());
    }

    // ---------- contact ----------

    @Test
    void contactReturnsEmailAndNameOnly() throws Exception {
        User user = newUser(Role.CUSTOMER);
        mvc.perform(get("/internal/v1/users/{id}/contact", user.getId()).header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId().toString()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.fullName").value("Ruwan Perera"))
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void contactForUnknownUserIs404Problem() throws Exception {
        mvc.perform(get("/internal/v1/users/{id}/contact", UUID.randomUUID()).header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("User not found"));
    }

    // ---------- introspection contract ----------

    @Test
    void approvedMerchantKeyReturnsMerchantJwt() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String apiKey = credentialService.issueApiKey(merchant, "Shop backend").secret();
        String userId = merchant.getUser().getId().toString();

        String body = introspect(apiKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(userId))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn().getResponse().getContentAsString();

        Jwt token = jwtDecoder.decode(JsonPath.read(body, "$.token"));
        assertThat(token.getSubject()).isEqualTo(userId);
        assertThat(token.getClaimAsString("merchantId")).isEqualTo(userId);
        assertThat(token.getClaimAsStringList("roles")).containsExactly("MERCHANT");
        assertThat(token.getClaimAsString("merchantStatus")).isEqualTo("APPROVED");
        assertThat(token.getClaimAsString("scope")).isEqualTo("orders:write");
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(TokenService.MERCHANT_API_TOKEN_TTL);
    }

    @Test
    void pendingMerchantKeyReturnsStatusButNoToken() throws Exception {
        String apiKey = credentialService.issueApiKey(newMerchant(false), "Shop backend").secret();
        introspect(apiKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void disabledMerchantUserReportsDisabledWithoutToken() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String apiKey = credentialService.issueApiKey(merchant, "Shop backend").secret();
        User user = users.findById(merchant.getUser().getId()).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        users.save(user);

        introspect(apiKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void unknownKeyIs401() throws Exception {
        introspect("sd_live_" + SecretTokens.random())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid API key"));
    }

    @Test
    void revokedKeyIs401() throws Exception {
        CredentialService.Issued issued = credentialService.issueApiKey(newMerchant(true), "Old key");
        ApiCredential credential = credentials.findById(issued.credential().getId()).orElseThrow();
        credential.revoke(java.time.Instant.now());
        credentials.save(credential);

        introspect(issued.secret()).andExpect(status().isUnauthorized());
    }

    @Test
    void blankApiKeyIs400() throws Exception {
        introspect("").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheHashIsStoredAndUseIsRecorded() throws Exception {
        CredentialService.Issued issued = credentialService.issueApiKey(newMerchant(true), "Shop backend");
        ApiCredential stored = credentials.findById(issued.credential().getId()).orElseThrow();

        assertThat(issued.secret()).startsWith(CredentialService.API_KEY_PREFIX);
        assertThat(stored.getSecretHash()).isEqualTo(SecretTokens.sha256(issued.secret())).doesNotContain(issued.secret());
        assertThat(issued.secret()).startsWith(stored.getKeyPrefix());
        assertThat(stored.getKeyPrefix()).hasSize(CredentialService.API_KEY_PREFIX.length() + CredentialService.VISIBLE_CHARS);
        assertThat(stored.getLastUsedAt()).isNull();

        introspect(issued.secret()).andExpect(status().isOk());
        assertThat(credentials.findById(stored.getId()).orElseThrow().getLastUsedAt()).isNotNull();
    }
}
