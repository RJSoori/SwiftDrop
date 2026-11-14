package com.swiftdrop.auth.credentials;

import com.jayway.jsonpath.JsonPath;
import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.config.AuthProperties;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.domain.UserStatus;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.auth.service.SecretTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase B: merchants create, list and revoke credentials; the client-credentials grant issues merchant JWTs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class CredentialFlowIntegrationTest {

    private static final String CREDENTIALS = "/api/v1/auth/merchant/credentials";
    private static final String TOKEN = "/api/v1/auth/oauth/token";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository users;
    @Autowired
    MerchantAccountRepository merchants;
    @Autowired
    ApiCredentialRepository credentials;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    CredentialService credentialService;
    @Autowired
    JwtDecoder jwtDecoder;
    @Autowired
    JwtEncoder jwtEncoder;
    @Autowired
    AuthProperties props;

    // ---------- helpers ----------

    private MerchantAccount newMerchant(boolean approved) {
        User user = authService.createUser("merchant-" + UUID.randomUUID() + "@example.com", "password1",
                "Ruwan Perera", "0771234567", Role.MERCHANT);
        MerchantAccount merchant = new MerchantAccount(user, "Ruwan Stores", "PV-" + UUID.randomUUID());
        if (approved) {
            merchant.approve(null);
        }
        return merchants.save(merchant);
    }

    /** A signed-in merchant's dashboard token. jwt() adds scope=read by default; a human session has no scope. */
    private static JwtRequestPostProcessor signedIn(MerchantAccount merchant) {
        return jwt().jwt(j -> j.subject(merchant.getUser().getId().toString()).claims(c -> c.remove("scope")))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    private ResultActions create(MerchantAccount merchant, String type, String name) throws Exception {
        return mvc.perform(post(CREDENTIALS).with(signedIn(merchant)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"%s\",\"name\":\"%s\"}".formatted(type, name)));
    }

    private String createdJson(MerchantAccount merchant, String type) throws Exception {
        return create(merchant, type, "Shop backend").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private ResultActions tokenWithForm(String clientId, String secret) throws Exception {
        return mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials").param("client_id", clientId).param("client_secret", secret));
    }

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    // ---------- create ----------

    @Test
    void approvedMerchantCreatesApiKeyShownOnceAndStoredOnlyAsHash() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = create(merchant, "API_KEY", "  Shop backend  ")
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.credential.type").value("API_KEY"))
                .andExpect(jsonPath("$.credential.name").value("Shop backend"))
                .andExpect(jsonPath("$.credential.scopes").value("orders:write"))
                .andExpect(jsonPath("$.credential.clientId").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String secret = JsonPath.read(body, "$.secret");
        String prefix = JsonPath.read(body, "$.credential.prefix");
        ApiCredential stored = credentials.findById(UUID.fromString(JsonPath.read(body, "$.credential.id"))).orElseThrow();
        assertThat(secret).startsWith(CredentialService.API_KEY_PREFIX).startsWith(prefix);
        assertThat(stored.getSecretHash()).isEqualTo(SecretTokens.sha256(secret));
        assertThat(List.of(stored.getName(), stored.getKeyPrefix(), stored.getSecretHash(), stored.getScopes()))
                .noneMatch(column -> column.contains(secret));

        // The listing never shows the secret again.
        String listing = mvc.perform(get(CREDENTIALS).with(signedIn(merchant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value(prefix))
                .andExpect(jsonPath("$[0].secret").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(listing).doesNotContain(secret).doesNotContain(stored.getSecretHash());
    }

    @Test
    void createdApiKeyWorksAtTheGatewayIntrospectEndpoint() throws Exception {
        String secret = JsonPath.read(createdJson(newMerchant(true), "API_KEY"), "$.secret");
        mvc.perform(post("/internal/v1/api-keys/introspect").header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"apiKey\":\"%s\"}".formatted(secret)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void createWritesAuditRow() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String id = JsonPath.read(createdJson(merchant, "API_KEY"), "$.credential.id");
        assertThat(auditLog.findByTarget("api_credential:" + id + ":API_KEY"))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getAction()).isEqualTo("API_CREDENTIAL_CREATED");
                    assertThat(e.getActorId()).isEqualTo(merchant.getUser().getId());
                });
    }

    @Test
    void pendingMerchantCannotCreateOrListCredentials() throws Exception {
        MerchantAccount pending = newMerchant(false);
        create(pending, "API_KEY", "Shop backend")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Merchant account is not approved"));
        mvc.perform(get(CREDENTIALS).with(signedIn(pending))).andExpect(status().isForbidden());
        assertThat(credentials.findByMerchant_IdOrderByCreatedAtDesc(pending.getId())).isEmpty();
    }

    @Test
    void disabledMerchantUserCannotCreateCredentials() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        User user = users.findById(merchant.getUser().getId()).orElseThrow();
        user.setStatus(UserStatus.DISABLED);
        users.save(user);
        create(merchant, "API_KEY", "Shop backend").andExpect(status().isForbidden());
    }

    @Test
    void otherRolesAndAnonymousAreRefused() throws Exception {
        for (String role : new String[]{"ROLE_CUSTOMER", "ROLE_ADMIN", "ROLE_COURIER", "ROLE_DISPATCHER"}) {
            mvc.perform(get(CREDENTIALS).with(jwt().authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get(CREDENTIALS)).andExpect(status().isUnauthorized());
    }

    @Test
    void machineTokenCannotManageCredentials() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        var machine = jwt().jwt(j -> j.subject(merchant.getUser().getId().toString()).claim("scope", "orders:write"))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
        mvc.perform(post(CREDENTIALS).with(machine).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"API_KEY\",\"name\":\"Sneaky\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get(CREDENTIALS).with(machine)).andExpect(status().isForbidden());
    }

    @Test
    void invalidCreateRequestsAre400WithFieldErrors() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        mvc.perform(post(CREDENTIALS).with(signedIn(merchant)).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field=='type')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='name')]").exists());
        create(merchant, "API_KEY", "x".repeat(81)).andExpect(status().isBadRequest());
        create(merchant, "PASSWORD", "Shop").andExpect(status().isBadRequest());
    }

    // ---------- list and revoke ----------

    @Test
    void merchantOnlySeesAndRevokesItsOwnCredentials() throws Exception {
        MerchantAccount mine = newMerchant(true);
        MerchantAccount other = newMerchant(true);
        String myId = JsonPath.read(createdJson(mine, "API_KEY"), "$.credential.id");
        String otherId = JsonPath.read(createdJson(other, "API_KEY"), "$.credential.id");

        mvc.perform(get(CREDENTIALS).with(signedIn(mine)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(myId));
        mvc.perform(delete(CREDENTIALS + "/{id}", otherId).with(signedIn(mine)))
                .andExpect(status().isNotFound());
        assertThat(credentials.findById(UUID.fromString(otherId)).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void revokeKeepsTheRowRejectsTheKeyAndIsIdempotent() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = createdJson(merchant, "API_KEY");
        String id = JsonPath.read(body, "$.credential.id");
        String secret = JsonPath.read(body, "$.secret");

        mvc.perform(delete(CREDENTIALS + "/{id}", id).with(signedIn(merchant))).andExpect(status().isNoContent());
        mvc.perform(delete(CREDENTIALS + "/{id}", id).with(signedIn(merchant))).andExpect(status().isNoContent());

        mvc.perform(get(CREDENTIALS).with(signedIn(merchant)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].revokedAt").isString());
        assertThat(auditLog.findByTarget("api_credential:" + id)).singleElement()
                .satisfies(e -> assertThat(e.getAction()).isEqualTo("API_CREDENTIAL_REVOKED"));
        assertThatThrownBy(() -> credentialService.introspect(secret))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    // ---------- client credentials grant ----------

    @Test
    void clientCredentialsGrantIssuesFifteenMinuteMerchantToken() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = createdJson(merchant, "CLIENT");
        String clientId = JsonPath.read(body, "$.credential.clientId");
        String secret = JsonPath.read(body, "$.secret");
        assertThat(clientId).startsWith(CredentialService.CLIENT_ID_PREFIX);
        assertThat(secret).startsWith(CredentialService.CLIENT_SECRET_PREFIX);

        String response = tokenWithForm(clientId, secret)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("orders:write"))
                .andReturn().getResponse().getContentAsString();

        String userId = merchant.getUser().getId().toString();
        Jwt token = jwtDecoder.decode(JsonPath.read(response, "$.access_token"));
        assertThat(token.getSubject()).isEqualTo(userId);
        assertThat(token.getClaimAsString(MerchantIdentity.CLAIM)).isEqualTo(userId);
        assertThat(token.getClaimAsStringList("roles")).containsExactly("MERCHANT");
        assertThat(token.getClaimAsString("merchantStatus")).isEqualTo("APPROVED");
        assertThat(token.getClaimAsString("scope")).isEqualTo("orders:write");
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));

        // The issued token is a valid bearer token for our resource servers...
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token.getTokenValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId));
        // ...but it is a machine token, so it cannot mint more credentials.
        mvc.perform(get(CREDENTIALS).header("Authorization", "Bearer " + token.getTokenValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    void clientCredentialsAcceptHttpBasic() throws Exception {
        String body = createdJson(newMerchant(true), "CLIENT");
        mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                        .header("Authorization", basic(JsonPath.read(body, "$.credential.clientId"), JsonPath.read(body, "$.secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString());
    }

    @Test
    void wrongSecretUnknownClientAndApiKeyAsSecretAreInvalidClient() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String clientId = JsonPath.read(createdJson(merchant, "CLIENT"), "$.credential.clientId");
        String apiKey = JsonPath.read(createdJson(merchant, "API_KEY"), "$.secret");

        tokenWithForm(clientId, "sd_secret_wrong")
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"))
                .andExpect(jsonPath("$.error").value("invalid_client"))
                .andExpect(jsonPath("$.detail").value("Invalid client credentials"));
        tokenWithForm("sd_client_unknown", "sd_secret_x").andExpect(status().isUnauthorized());
        tokenWithForm(clientId, apiKey).andExpect(status().isUnauthorized());
        tokenWithForm(apiKey, apiKey).andExpect(status().isUnauthorized());
    }

    @Test
    void revokedClientIsInvalidClient() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String body = createdJson(merchant, "CLIENT");
        mvc.perform(delete(CREDENTIALS + "/{id}", (String) JsonPath.read(body, "$.credential.id")).with(signedIn(merchant)))
                .andExpect(status().isNoContent());
        tokenWithForm(JsonPath.read(body, "$.credential.clientId"), JsonPath.read(body, "$.secret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    void pendingMerchantClientIsUnauthorizedClient() throws Exception {
        CredentialService.Issued issued = credentialService.issue(newMerchant(false), ApiCredentialType.CLIENT, "Old client");
        tokenWithForm(issued.credential().getKeyPrefix(), issued.secret())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test
    void unsupportedGrantAndMissingClientAreRejected() throws Exception {
        mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_grant_type"));
        mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mvc.perform(post(TOKEN).contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials")
                        .header("Authorization", "Basic not-base64!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredMerchantTokenIsRejected() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        Instant issuedAt = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(merchant.getUser().getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(Duration.ofMinutes(15)))
                .claim("roles", List.of("MERCHANT"))
                .claim("scope", "orders:write")
                .build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }
}
