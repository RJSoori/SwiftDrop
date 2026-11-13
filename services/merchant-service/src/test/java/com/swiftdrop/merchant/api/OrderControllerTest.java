package com.swiftdrop.merchant.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.merchant.service.OrderService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@WebMvcTest(OrderController.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class OrderControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrderService orders;

    static org.springframework.security.test.web.servlet.request
                    .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            merchant(UUID id, String status) {
        return jwt().jwt(j -> j.subject(id.toString()).claim("merchantStatus", status))
                .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"));
    }

    @Test
    void unapprovedMerchantsAndOtherRolesCannotUseOrders() throws Exception {
        for (String status : new String[] {"PENDING", "REJECTED", "SUSPENDED"})
            mvc.perform(get("/api/v1/merchant/orders").with(merchant(UUID.randomUUID(), status)))
                    .andExpect(status().isForbidden());
        mvc.perform(
                        get("/api/v1/merchant/orders")
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/merchant/orders")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void listAndDetailAlwaysUseAuthenticatedMerchantIdentity() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(
                        get("/api/v1/merchant/orders")
                                .param("merchantId", UUID.randomUUID().toString())
                                .with(merchant(id, "APPROVED")))
                .andExpect(status().isOk());
        verify(orders).list(id, null, 0, 20);
        mvc.perform(get("/api/v1/merchant/orders/SD-TEST").with(merchant(id, "APPROVED")))
                .andExpect(status().isOk());
        verify(orders).detail(id, "SD-TEST");
    }

    @Test
    void missingKeyAndInvalidPayloadAreRejectedBeforeCallingParcelService() throws Exception {
        mvc.perform(
                        post("/api/v1/merchant/orders")
                                .with(merchant(UUID.randomUUID(), "APPROVED"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        post("/api/v1/merchant/orders")
                                .with(merchant(UUID.randomUUID(), "APPROVED"))
                                .header("Idempotency-Key", "key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(orders);
    }
}
