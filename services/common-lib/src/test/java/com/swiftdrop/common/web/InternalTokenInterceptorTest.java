package com.swiftdrop.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InternalTokenInterceptorTest {

    @Test
    void addsInternalTokenHeader() {
        RestClient.Builder builder = RestClient.builder().requestInterceptor(new InternalTokenInterceptor("secret-123"));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth-service/internal/v1/ping"))
                .andExpect(header("X-Internal-Token", "secret-123"))
                .andRespond(withSuccess());

        builder.build().get().uri("http://auth-service/internal/v1/ping").retrieve().toBodilessEntity();

        server.verify();
    }
}
