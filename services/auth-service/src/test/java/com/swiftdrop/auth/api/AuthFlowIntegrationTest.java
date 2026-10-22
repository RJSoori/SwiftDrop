package com.swiftdrop.auth.api;

import com.swiftdrop.auth.TestcontainersConfig;
import com.swiftdrop.auth.repository.AuditLogRepository;
import com.swiftdrop.auth.repository.UserRepository;
import com.swiftdrop.auth.service.PiiEncryptor;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AuthFlowIntegrationTest {

    private static final String SEED_PASSWORD = "Seed-pass-123";

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    PiiEncryptor pii;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static String signupJson(String email) {
        return """
                {"email":"%s","password":"password1","fullName":"Nimal Perera","phone":"0771234567"}""".formatted(email);
    }

    private static String loginJson(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static Cookie cookieOf(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as(name).isNotNull();
        return cookie;
    }

    @Test
    void signupLoginMeRefreshLogout() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.merchantStatus").doesNotExist())
                .andExpect(cookie().exists(AuthCookies.ACCESS));

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email.toUpperCase(), "password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(cookie().httpOnly(AuthCookies.ACCESS, true))
                .andExpect(cookie().httpOnly(AuthCookies.REFRESH, true))
                .andExpect(cookie().path(AuthCookies.REFRESH, "/api/v1/auth"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.containsString("SameSite=Lax"))))
                .andReturn();
        Cookie access = cookieOf(login, AuthCookies.ACCESS);
        Cookie refresh = cookieOf(login, AuthCookies.REFRESH);

        mvc.perform(get("/api/v1/auth/me").cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nimal Perera"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access.getValue()))
                .andExpect(status().isOk());

        // A stale/garbage access cookie must not break the public refresh endpoint.
        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(refresh, new Cookie(AuthCookies.ACCESS, "expired.jwt.value")))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refresh2 = cookieOf(refreshed, AuthCookies.REFRESH);
        Cookie access2 = cookieOf(refreshed, AuthCookies.ACCESS);
        assertThat(refresh2.getValue()).isNotEqualTo(refresh.getValue());

        mvc.perform(post("/api/v1/auth/logout").cookie(access2, refresh2))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(AuthCookies.ACCESS, 0))
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));

        mvc.perform(post("/api/v1/auth/refresh").cookie(refresh2))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().maxAge(AuthCookies.REFRESH, 0));
    }

    @Test
    void reusingARotatedRefreshTokenRevokesTheFamily() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isCreated());
        Cookie first = cookieOf(login(email, "password1"), AuthCookies.REFRESH);
        Cookie second = cookieOf(mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isOk()).andReturn(),
                AuthCookies.REFRESH);

        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        // the attacker's replay also killed the legitimate newest token
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void badCredentialsReturnTheSameGeneric401() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(email, "wrongpass1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(uniqueEmail(), "password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void invalidSignupReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","fullName":"","phone":"12345"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'fullName')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists());
    }

    @Test
    void phoneIsEncryptedAtRest() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupJson(email)))
                .andExpect(status().isCreated());

        String stored = users.findByEmail(email).orElseThrow().getPhoneEncrypted();
        assertThat(stored).doesNotContain("771234567");
        assertThat(pii.decrypt(stored)).isEqualTo("+94771234567");
    }

    @Test
    void merchantIsPendingUntilAdminApproves() throws Exception {
        String email = uniqueEmail();
        MvcResult signup = mvc.perform(post("/api/v1/auth/signup/merchant").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1","fullName":"Shop Owner","phone":"+94771234567",
                                 "businessName":"Lanka Goods","registrationNo":"PV-12345"}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("MERCHANT"))
                .andExpect(jsonPath("$.merchantStatus").value("PENDING"))
                .andReturn();
        Cookie merchantRefresh = cookieOf(signup, AuthCookies.REFRESH);

        Cookie admin = cookieOf(login("admin@swiftdrop.test", SEED_PASSWORD), AuthCookies.ACCESS);
        String body = mvc.perform(get("/api/v1/auth/admin/merchants?status=PENDING").cookie(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String merchantId = com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body,
                "$[?(@.email == '%s')].id".formatted(email)).getFirst();

        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/approve", merchantId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/approve", merchantId).cookie(admin))
                .andExpect(status().isConflict());
        assertThat(auditLog.findByTarget("merchant_account:" + merchantId))
                .extracting(e -> e.getAction()).containsExactly("MERCHANT_APPROVED");

        // the next token the merchant gets carries the new status
        mvc.perform(post("/api/v1/auth/refresh").cookie(merchantRefresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantStatus").value("APPROVED"));
    }

    @Test
    void adminCanRejectMerchantWithReason() throws Exception {
        String email = uniqueEmail();
        mvc.perform(post("/api/v1/auth/signup/merchant").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1","fullName":"Shop Owner","phone":"0771234567",
                                 "businessName":"Fake Co","registrationNo":"PV-0"}""".formatted(email)))
                .andExpect(status().isCreated());
        Cookie admin = cookieOf(login("admin@swiftdrop.test", SEED_PASSWORD), AuthCookies.ACCESS);
        String body = mvc.perform(get("/api/v1/auth/admin/merchants").cookie(admin)).andReturn().getResponse().getContentAsString();
        String merchantId = com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body,
                "$[?(@.email == '%s')].id".formatted(email)).getFirst();

        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/reject", merchantId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/reject", merchantId).cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Registration number not found\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Registration number not found"));
        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/approve", UUID.randomUUID()).cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCreatesCourierWhoCanLogIn() throws Exception {
        String email = uniqueEmail();
        Cookie admin = cookieOf(login("admin@swiftdrop.test", SEED_PASSWORD), AuthCookies.ACCESS);

        mvc.perform(post("/api/v1/auth/admin/users").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1","fullName":"New Courier","phone":"0771234567","role":"ADMIN"}"""
                                .formatted(email)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("role"));
        mvc.perform(post("/api/v1/auth/admin/users").cookie(admin).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1","fullName":"New Courier","phone":"0771234567","role":"COURIER"}"""
                                .formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("COURIER"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(email, "password1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("COURIER"));
    }

    @Test
    void seededAccountsExistForEveryRole() throws Exception {
        for (String email : new String[]{"dispatcher@swiftdrop.test", "courier1@swiftdrop.test", "courier2@swiftdrop.test",
                "customer@swiftdrop.test"}) {
            login(email, SEED_PASSWORD);
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("merchant@swiftdrop.test", SEED_PASSWORD)))
                .andExpect(jsonPath("$.merchantStatus").value("APPROVED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("pending-merchant@swiftdrop.test", SEED_PASSWORD)))
                .andExpect(jsonPath("$.merchantStatus").value("PENDING"));
    }

    @Test
    void jwksIsPublic() throws Exception {
        mvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
    }
}
