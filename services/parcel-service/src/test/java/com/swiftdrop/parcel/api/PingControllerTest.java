package com.swiftdrop.parcel.api;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PingController.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class PingControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void requestWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/parcels/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void anyAuthenticatedRoleIsAllowed() throws Exception {
        for (String role : new String[]{"CUSTOMER", "MERCHANT", "COURIER", "DISPATCHER", "ADMIN"}) {
            mvc.perform(get("/api/v1/parcels/ping").with(jwt()
                            .jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", java.util.List.of(role)))
                            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.roles[0]").value(role));
        }
    }
}
