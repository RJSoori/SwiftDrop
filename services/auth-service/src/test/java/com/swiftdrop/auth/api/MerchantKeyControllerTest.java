package com.swiftdrop.auth.api;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.swiftdrop.auth.service.MerchantKeyService;
import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@WebMvcTest(
        value = {MerchantKeyController.class, ApiKeyIntrospectionController.class},
        properties = "INTERNAL_TOKEN=test-internal")
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class MerchantKeyControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean MerchantKeyService keys;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;

    @Test
    void pendingMerchantCannotCreateAnApiKey() throws Exception {
        mvc.perform(
                        post("/api/v1/auth/merchant/api-keys")
                                .with(
                                        jwt().jwt(
                                                        j ->
                                                                j.subject(
                                                                                UUID.randomUUID()
                                                                                        .toString())
                                                                        .claim(
                                                                                "merchantStatus",
                                                                                "PENDING"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_MERCHANT"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(keys);
    }

    @Test
    void createdKeyIsShownOnceAndNeverCached() throws Exception {
        UUID id = UUID.randomUUID();
        when(keys.create(id))
                .thenReturn(
                        new MerchantKeyService.NewKey(
                                UUID.randomUUID(), "sd_live_test", "sd_live_t"));
        mvc.perform(
                        post("/api/v1/auth/merchant/api-keys")
                                .with(
                                        jwt().jwt(
                                                        j ->
                                                                j.subject(id.toString())
                                                                        .claim(
                                                                                "merchantStatus",
                                                                                "APPROVED"))
                                                .authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_MERCHANT"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.apiKey").value("sd_live_test"));
        verify(keys).create(id);
    }

    @Test
    void onlyInternalTokenCanIntrospectShopKeys() throws Exception {
        mvc.perform(
                        post("/internal/v1/api-keys/introspect")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"apiKey\":\"key\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/internal/v1/api-keys/introspect")
                                .header("X-Internal-Token", "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"apiKey\":\"key\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(keys);
        mvc.perform(
                        post("/internal/v1/api-keys/introspect")
                                .header("X-Internal-Token", "test-internal")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"apiKey\":\"key\"}"))
                .andExpect(status().isOk());
        verify(keys).introspect("key");
    }
}
