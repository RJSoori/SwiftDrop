package com.swiftdrop.merchant.api;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingController.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class PingControllerTest {

    @Autowired
    MockMvc mvc;

    private static JwtRequestPostProcessor merchant(String merchantStatus) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("merchantStatus", merchantStatus))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    @Test
    void pendingMerchantIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/merchant/ping").with(merchant("PENDING"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchant/ping").with(merchant("REJECTED"))).andExpect(status().isForbidden());
    }

    @Test
    void approvedMerchantIsAllowed() throws Exception {
        mvc.perform(get("/api/v1/merchant/ping").with(merchant("APPROVED"))).andExpect(status().isOk());
    }

    @Test
    void otherRolesAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/merchant/ping").with(jwt().jwt(j -> j.claim("merchantStatus", "APPROVED"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/merchant/ping")).andExpect(status().isUnauthorized());
    }
}
