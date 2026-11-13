package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierController.class)
@AutoConfigureMockMvc
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class CourierControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CourierService courierService;

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void courierCanReachCourierApiButOtherRolesAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/courier/deliveries/today").with(as("COURIER")))
                .andExpect(status().isOk());
        for (String role : new String[]{"CUSTOMER", "DISPATCHER"}) {
            mvc.perform(get("/api/v1/courier/deliveries/today").with(as(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/status", UUID.randomUUID())
                            .with(as(role))
                            .contentType("application/json")
                            .content("{\"toStatus\":\"IN_TRANSIT\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void courierNotAssignedToParcelGetsForbiddenForReadAndUpdate() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(courierService.deliveryDetails(parcelId))
                .thenThrow(new AccessDeniedException("Courier is not assigned to this parcel"));
        when(courierService.updateStatus(eq(parcelId), any()))
                .thenThrow(new AccessDeniedException("Courier is not assigned to this parcel"));

        mvc.perform(get("/api/v1/courier/deliveries/{parcelId}", parcelId).with(as("COURIER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"toStatus\":\"IN_TRANSIT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void statusEndpointRejectsStatusesOutsideCourierTransitions() throws Exception {
        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/status", UUID.randomUUID())
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"toStatus\":\"DELIVERED\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courierService);
    }

    @Test
    void otherFailureRequiresANote() throws Exception {
        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/fail", UUID.randomUUID())
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"reasonCode\":\"OTHER\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/fail", UUID.randomUUID())
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"note\":\"Missing reason\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courierService);
    }

    @Test
    void deliverWithoutProofIsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/deliver", UUID.randomUUID())
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"otp\":\"1234\",\"recipientName\":\"Recipient\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(courierService);
    }
}
