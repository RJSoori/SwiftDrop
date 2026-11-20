package com.swiftdrop.common.security;

import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
        "INTERNAL_TOKEN=test-internal-token",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/jwks"})
@ContextConfiguration(classes = InternalTokenSecurityTest.Endpoints.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class InternalTokenSecurityTest {

    @RestController
    static class Endpoints {
        @GetMapping("/internal/v1/ping")
        String internal() {
            return "internal-ok";
        }

        @GetMapping("/api/v1/ping")
        String api() {
            return "api-ok";
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void missingTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/internal/v1/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void wrongTokenIs401() throws Exception {
        mvc.perform(get("/internal/v1/ping").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctTokenIsAllowedWithoutJwt() throws Exception {
        mvc.perform(get("/internal/v1/ping").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(content().string("internal-ok"));
    }

    @Test
    void internalTokenDoesNotOpenOtherPaths() throws Exception {
        mvc.perform(get("/api/v1/ping").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ping").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void blankConfiguredTokenRejectsEverything() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/v1/ping");
        request.addHeader("X-Internal-Token", "");

        assertThat(new InternalTokenAuthorizationManager("").check(() -> null, new RequestAuthorizationContext(request))
                .isGranted()).isFalse();
        assertThat(new InternalTokenAuthorizationManager(null).check(() -> null, new RequestAuthorizationContext(request))
                .isGranted()).isFalse();
    }
}
