package com.swiftdrop.dispatch.api;

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

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void courierIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/dispatch/ping").with(as("COURIER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/dispatch/ping").with(as("CUSTOMER"))).andExpect(status().isForbidden());
    }

    @Test
    void dispatcherAndAdminAreAllowed() throws Exception {
        mvc.perform(get("/api/v1/dispatch/ping").with(as("DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dispatch/ping").with(as("ADMIN"))).andExpect(status().isOk());
    }

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/dispatch/ping")).andExpect(status().isUnauthorized());
    }
}
