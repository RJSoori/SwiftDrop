package com.swiftdrop.dispatch.courier;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(CourierController.class)
@AutoConfigureMockMvc
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
class CourierControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    CourierService courierService;

    @Test
    void returnsAssignedParcelDetails() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(courierService.deliveryDetails(parcelId)).thenReturn(
                new CourierModels.DeliveryDetails(parcelId, "IN_TRANSIT", "Demo Recipient", "Campus",
                        "12 Example Road", "Colombo"));

        mvc.perform(get("/api/v1/courier/deliveries/{parcelId}", parcelId).with(as("COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.recipientName").value("Demo Recipient"))
                .andExpect(jsonPath("$.dropoffAddress").value("12 Example Road"))
                .andExpect(jsonPath("$.dropoffCity").value("Colombo"));
    }

    @Test
    void updatesStatusAndReturnsTheNewStatus() throws Exception {
        UUID parcelId = UUID.randomUUID();
        when(courierService.updateStatus(eq(parcelId), any())).thenReturn(CourierModels.DeliveryStatus.IN_TRANSIT);

        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/status", parcelId)
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"toStatus\":\"IN_TRANSIT\",\"note\":\"Arrived at hub\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"));
    }

    @Test
    void uploadsProofAndReturnsCreatedResponse() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        when(courierService.uploadProof(eq(parcelId), any())).thenReturn(proofId);
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/v1/courier/deliveries/{parcelId}/proof", parcelId)
                        .file(file)
                        .with(as("COURIER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proofId").value(proofId.toString()));

        verify(courierService).uploadProof(eq(parcelId), any());
    }

    @Test
    void deliverOutcomeReturnsDelivered() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        when(courierService.deliver(parcelId, "1234", proofId, "Recipient"))
                .thenReturn(CourierModels.DeliveryOutcome.DELIVERED);

        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/deliver", parcelId)
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"otp\":\"1234\",\"proofId\":\"" + proofId + "\",\"recipientName\":\"Recipient\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()));
    }

    @Test
    void deliverOutcomeMapsInvalidOtpToBadRequest() throws Exception {
        assertDeliverOutcome(CourierModels.DeliveryOutcome.INVALID_OTP, "Incorrect OTP");
    }

    @Test
    void deliverOutcomeMapsLockedOtpToBadRequest() throws Exception {
        assertDeliverOutcome(CourierModels.DeliveryOutcome.OTP_LOCKED, "OTP is locked after too many attempts");
    }

    @Test
    void deliverOutcomeMapsExpiredOtpToBadRequest() throws Exception {
        assertDeliverOutcome(CourierModels.DeliveryOutcome.OTP_EXPIRED, "OTP has expired");
    }

    @Test
    void deliverOutcomeMapsAlreadyVerifiedOtpToBadRequest() throws Exception {
        assertDeliverOutcome(CourierModels.DeliveryOutcome.OTP_ALREADY_VERIFIED, "OTP has already been used");
    }

    @Test
    void marksDeliveryFailed() throws Exception {
        UUID parcelId = UUID.randomUUID();

        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/fail", parcelId)
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"reasonCode\":\"REFUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()));

        verify(courierService).failDelivery(eq(parcelId), any());
    }

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

    private void assertDeliverOutcome(CourierModels.DeliveryOutcome outcome, String expectedDetail) throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        when(courierService.deliver(parcelId, "1234", proofId, "Recipient")).thenReturn(outcome);

        mvc.perform(post("/api/v1/courier/deliveries/{parcelId}/deliver", parcelId)
                        .with(as("COURIER"))
                        .contentType("application/json")
                        .content("{\"otp\":\"1234\",\"proofId\":\"" + proofId + "\",\"recipientName\":\"Recipient\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(expectedDetail));
    }
}
