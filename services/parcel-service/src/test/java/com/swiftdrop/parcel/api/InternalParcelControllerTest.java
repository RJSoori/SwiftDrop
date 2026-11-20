package com.swiftdrop.parcel.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.parcel.config.SecurityConfig;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.service.ParcelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = InternalParcelController.class, properties = "INTERNAL_TOKEN=test-internal-token")
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
@Import(SecurityConfig.class)
class InternalParcelControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelService parcelService;

    @MockBean
    private com.swiftdrop.parcel.service.MerchantParcelCreation merchantCreation;

    @Test
    @DisplayName("POST /internal/v1/parcels - allows merchant-service to create parcel via internal call")
    void createMerchantParcel_success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        InternalCreateParcelRequest request = new InternalCreateParcelRequest(
                merchantId, "ORDER-1001", "Colombo Warehouse", "Colombo",
                new BigDecimal("6.9271"), new BigDecimal("79.8612"),
                "Kandy Customer", "Kandy", new BigDecimal("7.2906"), new BigDecimal("80.6337"),
                "Saman Kumara", "+94712345678",
                new BigDecimal("1.5"), new BigDecimal("20.0"), new BigDecimal("15.0"), new BigDecimal("10.0"),
                "MEDIUM", null, null, null, "STANDARD"
        );

        Parcel parcel = new Parcel(
                "SD-20261108-MCH001", null, new BigDecimal("1.5"),
                "MEDIUM", new BigDecimal("470.00"), "Colombo Warehouse", "Kandy Customer"
        );
        parcel.setOrigin("MERCHANT");
        parcel.setMerchantId(merchantId);
        parcel.setMerchantOrderRef("ORDER-1001");
        parcel.setRecipientName("Saman Kumara");

        when(merchantCreation.create(any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(ParcelResponse.from(parcel));

        mvc.perform(post("/internal/v1/parcels").header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value("SD-20261108-MCH001"))
                .andExpect(jsonPath("$.origin").value("MERCHANT"))
                .andExpect(jsonPath("$.merchantOrderRef").value("ORDER-1001"));
    }

    @Test
    void merchantCreationRejectsCallsWithoutInternalToken() throws Exception {
        mvc.perform(post("/internal/v1/parcels").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /internal/v1/parcels/{id}/status - invalid transition returns 409 Conflict")
    void updateStatus_illegalTransitionReturns409() throws Exception {
        UUID parcelId = UUID.randomUUID();
        InternalUpdateStatusRequest request = new InternalUpdateStatusRequest(
                ParcelStatus.DELIVERED, UUID.randomUUID(), "Direct to delivered", null, null, null
        );

        when(parcelService.updateStatusInternal(eq(parcelId), any()))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Illegal status transition from CREATED to DELIVERED"));

        mvc.perform(post("/internal/v1/parcels/" + parcelId + "/status").header("X-Internal-Token", "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("GET /internal/v1/parcels/{id}/contact - returns recipient contact info")
    void getRecipientContact_success() throws Exception {
        UUID parcelId = UUID.randomUUID();
        RecipientContactResponse response = new RecipientContactResponse(
                "Nimal Silva", "+94770001122", "12 Lake Road", "Kandy");

        when(parcelService.getRecipientContact(parcelId)).thenReturn(response);

        mvc.perform(get("/internal/v1/parcels/" + parcelId + "/contact").header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipientName").value("Nimal Silva"))
                .andExpect(jsonPath("$.recipientPhone").value("+94770001122"))
                .andExpect(jsonPath("$.dropoffAddress").value("12 Lake Road"))
                .andExpect(jsonPath("$.dropoffCity").value("Kandy"));
    }

    @Test
    @DisplayName("Internal status and contact endpoints reject a missing or wrong X-Internal-Token")
    void statusAndContactNeedTheInternalToken() throws Exception {
        UUID parcelId = UUID.randomUUID();
        mvc.perform(post("/internal/v1/parcels/" + parcelId + "/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/parcels/" + parcelId + "/contact")).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/v1/parcels/" + parcelId + "/contact").header("X-Internal-Token", "wrong"))
                .andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(parcelService);
    }
}
