package com.swiftdrop.auth.credentials;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.auth.service.SecretTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/v1/auth/merchant/api-keys keeps the request and response shapes the merchant Webhooks page uses, and its keys
 * are the same credentials the gateway introspects. Replaces the earlier MerchantKeyControllerTest/ServiceTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class MerchantApiKeysControllerTest {

    private static final String KEYS = "/api/v1/auth/merchant/api-keys";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;
    @Autowired
    MerchantAccountRepository merchants;
    @Autowired
    ApiCredentialRepository credentials;
    @Autowired
    CredentialService credentialService;

    private MerchantAccount newMerchant(boolean approved) {
        MerchantAccount merchant = new MerchantAccount(
                authService.createUser("shop-" + UUID.randomUUID() + "@example.com", "password1", "Kamal Silva",
                        "0771234567", Role.MERCHANT),
                "Kamal Traders", "PV-" + UUID.randomUUID());
        if (approved) {
            merchant.approve(null);
        }
        return merchants.save(merchant);
    }

    private static JwtRequestPostProcessor signedIn(MerchantAccount merchant) {
        return jwt().jwt(j -> j.subject(merchant.getUser().getId().toString()).claims(c -> c.remove("scope")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private ResultActions introspect(String apiKey) throws Exception {
        return mvc.perform(post("/internal/v1/api-keys/introspect").header("X-Internal-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"%s\"}".formatted(apiKey)));
    }

    @Test
    void createdKeyIsShownOnceNeverCachedAndStoredOnlyAsHash() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = mvc.perform(post(KEYS).with(signedIn(merchant)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.prefix").isString())
                .andReturn().getResponse().getContentAsString();

        String apiKey = JsonPath.read(body, "$.apiKey");
        assertThat(apiKey).startsWith("sd_live_").startsWith(JsonPath.<String>read(body, "$.prefix"));
        ApiCredential stored = credentials.findById(UUID.fromString(JsonPath.read(body, "$.id"))).orElseThrow();
        assertThat(stored.getSecretHash()).isEqualTo(SecretTokens.sha256(apiKey));

        String listing = mvc.perform(get(KEYS).with(signedIn(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(stored.getId().toString()))
                .andExpect(jsonPath("$[0].prefix").value(stored.getKeyPrefix()))
                .andExpect(jsonPath("$[0].createdAt").isString())
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(listing).doesNotContain(apiKey);
    }

    @Test
    void keyFromThisEndpointWorksAtTheGatewayAndRevokingStopsIt() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = mvc.perform(post(KEYS).with(signedIn(merchant))).andReturn().getResponse().getContentAsString();
        String apiKey = JsonPath.read(body, "$.apiKey");

        introspect(apiKey).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(delete(KEYS + "/{id}", (String) JsonPath.read(body, "$.id")).with(signedIn(merchant)))
                .andExpect(status().isNoContent());
        introspect(apiKey).andExpect(status().isUnauthorized());
        mvc.perform(get(KEYS).with(signedIn(merchant))).andExpect(jsonPath("$[0].revokedAt").isString());
    }

    @Test
    void listShowsOnlyApiKeysNotOAuthClients() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        credentialService.create(merchant.getUser().getId(), ApiCredentialType.CLIENT, "Backend client");
        credentialService.create(merchant.getUser().getId(), ApiCredentialType.API_KEY, "Shop");
        mvc.perform(get(KEYS).with(signedIn(merchant)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].prefix").value(org.hamcrest.Matchers.startsWith("sd_live_")));
    }

    @Test
    void pendingMerchantMachineTokenAndOtherRolesAreRefused() throws Exception {
        mvc.perform(post(KEYS).with(signedIn(newMerchant(false)))).andExpect(status().isForbidden());

        MerchantAccount approved = newMerchant(true);
        var machine = jwt().jwt(j -> j.subject(approved.getUser().getId().toString()).claim("scope", "orders:write"))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
        mvc.perform(post(KEYS).with(machine)).andExpect(status().isForbidden());

        mvc.perform(get(KEYS).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(KEYS)).andExpect(status().isUnauthorized());
    }

    @Test
    void merchantCannotRevokeAnotherMerchantsKey() throws Exception {
        MerchantAccount owner = newMerchant(true);
        String body = mvc.perform(post(KEYS).with(signedIn(owner))).andReturn().getResponse().getContentAsString();
        mvc.perform(delete(KEYS + "/{id}", (String) JsonPath.read(body, "$.id")).with(signedIn(newMerchant(true))))
                .andExpect(status().isNotFound());
        introspect(JsonPath.read(body, "$.apiKey")).andExpect(status().isOk());
    }
}
