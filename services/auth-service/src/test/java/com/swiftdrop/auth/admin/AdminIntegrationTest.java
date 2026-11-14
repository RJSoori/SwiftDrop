package com.swiftdrop.auth.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.api.AuthCookies;
import com.swiftdrop.auth.credentials.ApiCredentialType;
import com.swiftdrop.auth.credentials.CredentialService;
import com.swiftdrop.auth.domain.MerchantAccount;
import com.swiftdrop.auth.domain.Role;
import com.swiftdrop.auth.domain.User;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.MerchantAccountRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.AuthService;
import com.swiftdrop.common.outbox.OutboxEvent;
import com.swiftdrop.common.outbox.OutboxEventRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase C: admin user management, merchant suspend/reinstate, audit log and the merchant.approved outbox event. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AdminIntegrationTest {

    private static final String ADMIN = "/api/v1/auth/admin";

    @Autowired
    MockMvc mvc;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository users;
    @Autowired
    MerchantAccountRepository merchants;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    OutboxEventRepository outbox;
    @Autowired
    CredentialService credentialService;
    @Autowired
    ObjectMapper objectMapper;

    /** A real admin: merchant_accounts.reviewed_by is a foreign key to users, so the actor must exist. */
    private UUID adminId;

    @BeforeEach
    void seededAdmin() {
        adminId = users.findByEmail("admin@swiftdrop.test").orElseThrow().getId();
    }

    private JwtRequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject(adminId.toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private User newUser(Role role, String tag) {
        return authService.createUser(tag + "-" + UUID.randomUUID() + "@example.com", "password1", "Nimal " + tag,
                "0771234567", role);
    }

    private MerchantAccount newMerchant(boolean approved) {
        MerchantAccount merchant = new MerchantAccount(newUser(Role.MERCHANT, "shop"), "Nimal Stores", "PV-" + UUID.randomUUID());
        if (approved) {
            merchant.approve(null);
        }
        return merchants.save(merchant);
    }

    // ---------- user list ----------

    @Test
    void listFiltersByRoleShowsMerchantStatusAndMasksPhones() throws Exception {
        MerchantAccount merchant = newMerchant(false);
        String email = merchant.getUser().getEmail();

        mvc.perform(get(ADMIN + "/users").param("role", "MERCHANT").param("q", email).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email))
                .andExpect(jsonPath("$.content[0].roles[0]").value("MERCHANT"))
                .andExpect(jsonPath("$.content[0].merchantStatus").value("PENDING"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].phone").value("+9477*****67"));

        mvc.perform(get(ADMIN + "/users").param("role", "COURIER").param("q", email).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void searchIsCaseInsensitiveAndPagingWorks() throws Exception {
        String tag = "pg" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 3; i++) {
            newUser(Role.CUSTOMER, tag);
        }
        mvc.perform(get(ADMIN + "/users").param("q", tag.toUpperCase()).param("size", "2").with(admin()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.size").value(2));
        mvc.perform(get(ADMIN + "/users").param("q", tag).param("size", "2").param("page", "1").with(admin()))
                .andExpect(jsonPath("$.content.length()").value(1));
        // Wildcards in the search text are literal.
        mvc.perform(get(ADMIN + "/users").param("q", "%").param("role", "CUSTOMER").with(admin()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidFilterIs400AndNonAdminsAre403() throws Exception {
        mvc.perform(get(ADMIN + "/users").param("role", "PILOT").with(admin())).andExpect(status().isBadRequest());
        for (String role : new String[]{"ROLE_CUSTOMER", "ROLE_MERCHANT", "ROLE_COURIER", "ROLE_DISPATCHER"}) {
            var other = jwt().authorities(new SimpleGrantedAuthority(role));
            mvc.perform(get(ADMIN + "/users").with(other)).andExpect(status().isForbidden());
            mvc.perform(get(ADMIN + "/audit-logs").with(other)).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/users/{id}/disable", UUID.randomUUID()).with(other)).andExpect(status().isForbidden());
            mvc.perform(post(ADMIN + "/merchants/{id}/suspend", UUID.randomUUID()).with(other)).andExpect(status().isForbidden());
        }
        mvc.perform(get(ADMIN + "/users")).andExpect(status().isUnauthorized());
    }

    // ---------- disable / enable ----------

    @Test
    void disablingSignsTheUserOutAndBlocksLoginUntilEnabled() throws Exception {
        User user = newUser(Role.CUSTOMER, "dis");
        String login = "{\"email\":\"%s\",\"password\":\"password1\"}".formatted(user.getEmail());
        Cookie refresh = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie(AuthCookies.REFRESH);

        mvc.perform(post(ADMIN + "/users/{id}/disable", user.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isUnauthorized());

        mvc.perform(post(ADMIN + "/users/{id}/enable", user.getId()).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk());

        assertThat(auditLog.findByTarget("user:" + user.getId())).extracting("action")
                .containsExactlyInAnyOrder("USER_DISABLED", "USER_ENABLED");
    }

    @Test
    void disablingTwiceWritesOneAuditRow() throws Exception {
        User user = newUser(Role.COURIER, "twice");
        mvc.perform(post(ADMIN + "/users/{id}/disable", user.getId()).with(admin())).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/users/{id}/disable", user.getId()).with(admin())).andExpect(status().isOk());
        assertThat(auditLog.findByTarget("user:" + user.getId())).hasSize(1);
    }

    @Test
    void adminCannotDisableThemselvesAndUnknownUserIs404() throws Exception {
        User admin = newUser(Role.ADMIN, "self");
        var self = jwt().jwt(j -> j.subject(admin.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        mvc.perform(post(ADMIN + "/users/{id}/disable", admin.getId()).with(self))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("You cannot disable your own account"));
        mvc.perform(post(ADMIN + "/users/{id}/enable", UUID.randomUUID()).with(admin())).andExpect(status().isNotFound());
    }

    // ---------- merchant suspend / reinstate ----------

    @Test
    void suspendStopsTheMerchantsKeysAndReinstateRestoresThem() throws Exception {
        MerchantAccount merchant = newMerchant(true);
        String apiKey = credentialService.create(merchant.getUser().getId(), ApiCredentialType.API_KEY, "Shop").secret();

        mvc.perform(post(ADMIN + "/merchants/{id}/suspend", merchant.getId()).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
        assertThat(credentialService.introspect(apiKey).status()).isEqualTo("SUSPENDED");
        assertThat(credentialService.introspect(apiKey).token()).isNull();
        mvc.perform(get(ADMIN + "/merchants").param("status", "SUSPENDED").with(admin()))
                .andExpect(jsonPath("$[?(@.id=='%s')]".formatted(merchant.getId())).exists());

        mvc.perform(post(ADMIN + "/merchants/{id}/reinstate", merchant.getId()).with(admin()))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(credentialService.introspect(apiKey).token()).isNotNull();

        assertThat(auditLog.findByTarget("merchant_account:" + merchant.getId())).extracting("action")
                .containsExactlyInAnyOrder("MERCHANT_SUSPENDED", "MERCHANT_REINSTATED");
    }

    @Test
    void suspendAndReinstateRejectWrongStates() throws Exception {
        MerchantAccount pending = newMerchant(false);
        mvc.perform(post(ADMIN + "/merchants/{id}/suspend", pending.getId()).with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Merchant application is already PENDING"));
        mvc.perform(post(ADMIN + "/merchants/{id}/reinstate", newMerchant(true).getId()).with(admin()))
                .andExpect(status().isConflict());
        mvc.perform(post(ADMIN + "/merchants/{id}/suspend", UUID.randomUUID()).with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---------- merchant.approved outbox event ----------

    @Test
    void approvingWritesAuditRowAndMerchantApprovedEnvelopeToTheOutbox() throws Exception {
        MerchantAccount merchant = newMerchant(false);
        UUID merchantUserId = merchant.getUser().getId();

        mvc.perform(post(ADMIN + "/merchants/{id}/approve", merchant.getId()).with(admin()))
                .andExpect(status().isOk());

        assertThat(auditLog.findByTarget("merchant_account:" + merchant.getId())).extracting("action")
                .containsExactly("MERCHANT_APPROVED");

        List<OutboxEvent> events = outbox.findAll().stream().filter(e -> merchantUserId.equals(e.getAggregateId())).toList();
        assertThat(events).singleElement().satisfies(e -> {
            assertThat(e.getEventType()).isEqualTo("merchant.approved");
            assertThat(e.getPublishedAt()).isNull();
            JsonNode envelope = objectMapper.readTree(e.getPayload());
            assertThat(UUID.fromString(envelope.get("eventId").asText())).isNotNull();
            assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
            assertThat(envelope.get("eventType").asText()).isEqualTo("merchant.approved");
            assertThat(envelope.get("producer").asText()).isEqualTo("auth-service");
            assertThat(envelope.get("occurredAt").asText()).endsWith("Z");
            assertThat(envelope.get("data").get("merchantId").asText()).isEqualTo(merchantUserId.toString());
            // Events carry ids only: no email, name, phone or business details.
            assertThat(envelope.get("data").size()).isEqualTo(1);
            assertThat(e.getPayload()).doesNotContain(merchant.getUser().getEmail()).doesNotContain("Nimal");
        });
    }

    @Test
    void rejectingWritesNoOutboxEvent() throws Exception {
        MerchantAccount merchant = newMerchant(false);
        mvc.perform(post(ADMIN + "/merchants/{id}/reject", merchant.getId()).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Registration number not found\"}"))
                .andExpect(status().isOk());
        assertThat(outbox.findAll()).noneMatch(e -> merchant.getUser().getId().equals(e.getAggregateId()));
    }

    // ---------- audit log ----------

    @Test
    void auditLogListsNewestFirstWithActorEmailAndFilters() throws Exception {
        User actor = users.findByEmail("admin@swiftdrop.test").orElseThrow();
        var seededAdmin = jwt().jwt(j -> j.subject(actor.getId().toString())).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        User first = newUser(Role.CUSTOMER, "aud1");
        User second = newUser(Role.CUSTOMER, "aud2");
        mvc.perform(post(ADMIN + "/users/{id}/disable", first.getId()).with(seededAdmin)).andExpect(status().isOk());
        mvc.perform(post(ADMIN + "/users/{id}/disable", second.getId()).with(seededAdmin)).andExpect(status().isOk());

        mvc.perform(get(ADMIN + "/audit-logs").param("actorId", actor.getId().toString()).param("action", "user_disabled")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].target").value("user:" + second.getId()))
                .andExpect(jsonPath("$.content[1].target").value("user:" + first.getId()))
                .andExpect(jsonPath("$.content[0].action").value("USER_DISABLED"))
                .andExpect(jsonPath("$.content[0].actorEmail").value("admin@swiftdrop.test"))
                .andExpect(jsonPath("$.content[0].occurredAt").isString());
    }
}
