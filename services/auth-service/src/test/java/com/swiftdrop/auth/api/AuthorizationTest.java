package com.swiftdrop.auth.api;

import com.swiftdrop.auth.TestcontainersConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
class AuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void customerCannotCallAdminEndpoints() throws Exception {
        var customer = jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        mvc.perform(get("/api/v1/auth/admin/merchants").with(customer))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access denied"));
        mvc.perform(post("/api/v1/auth/admin/users").with(customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/admin/merchants/{id}/approve", UUID.randomUUID()).with(customer))
                .andExpect(status().isForbidden());
    }

    @Test
    void merchantAndCourierCannotCallAdminEndpoints() throws Exception {
        for (String role : new String[]{"ROLE_MERCHANT", "ROLE_COURIER", "ROLE_DISPATCHER"}) {
            mvc.perform(get("/api/v1/auth/admin/merchants").with(jwt().authorities(new SimpleGrantedAuthority(role))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void adminCanListMerchants() throws Exception {
        mvc.perform(get("/api/v1/auth/admin/merchants").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void requestsWithoutTokenGet401() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Authentication required"));
        mvc.perform(get("/api/v1/auth/admin/merchants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedTokenGets401() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0."))
                .andExpect(status().isUnauthorized());
    }
}
