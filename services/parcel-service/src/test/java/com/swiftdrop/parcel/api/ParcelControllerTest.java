package com.swiftdrop.parcel.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.parcel.config.SecurityConfig;
import com.swiftdrop.parcel.domain.Parcel;
import com.swiftdrop.parcel.domain.ParcelStatus;
import com.swiftdrop.parcel.domain.ParcelStatusHistory;
import com.swiftdrop.parcel.domain.PricingStrategyType;
import com.swiftdrop.parcel.dto.*;
import com.swiftdrop.parcel.service.ParcelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ParcelController.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
@Import(SecurityConfig.class)
class ParcelControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ParcelService parcelService;

    @Test
    @DisplayName("POST /api/v1/parcels/quote - public fee calculation succeeds without authentication")
    void calculateQuote_publicAccess() throws Exception {
        QuoteRequest request = new QuoteRequest(
                new BigDecimal("2.5"),
                new BigDecimal("20.0"),
                new BigDecimal("15.0"),
                new BigDecimal("10.0"),
                PricingStrategyType.WEIGHT_TIERED,
                null,
                null
        );

        QuoteResponse response = QuoteResponse.of(
                new BigDecimal("550.00"),
                new BigDecimal("2.5"),
                new BigDecimal("0.60"),
                new BigDecimal("2.5"),
                PricingStrategyType.WEIGHT_TIERED
        );

        when(parcelService.calculateQuote(any())).thenReturn(response);

        mvc.perform(post("/api/v1/parcels/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feeLkr").value(550.00))
                .andExpect(jsonPath("$.chargeableWeightKg").value(2.5));
    }

    @Test
    @DisplayName("POST /api/v1/parcels - unauthenticated booking request returns 401")
    void createParcel_unauthenticated() throws Exception {
        CreateParcelRequest request = new CreateParcelRequest(
                "Pickup addr", "Dropoff addr", new BigDecimal("1.0"),
                null, null, null, "SMALL", PricingStrategyType.WEIGHT_TIERED,
                null, null, null
        );

        mvc.perform(post("/api/v1/parcels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/parcels - authenticated customer can create a parcel")
    void createParcel_authenticated() throws Exception {
        UUID customerId = UUID.randomUUID();
        CreateParcelRequest request = new CreateParcelRequest(
                "123 Galle Rd, Colombo", "45 Main St, Kandy", new BigDecimal("2.0"),
                new BigDecimal("20.0"), new BigDecimal("15.0"), new BigDecimal("10.0"),
                "MEDIUM", PricingStrategyType.WEIGHT_TIERED, null, null, null
        );

        Parcel parcel = new Parcel(
                "SD-20261107-ABC123", customerId, new BigDecimal("2.0"),
                "MEDIUM", new BigDecimal("510.00"), "123 Galle Rd, Colombo", "45 Main St, Kandy"
        );
        ParcelResponse response = ParcelResponse.from(parcel);

        when(parcelService.createParcel(any(), eq(customerId))).thenReturn(response);

        mvc.perform(post("/api/v1/parcels")
                        .with(jwt().jwt(j -> j.subject(customerId.toString()).claim("roles", List.of("CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNo").value("SD-20261107-ABC123"))
                .andExpect(jsonPath("$.feeLkr").value(510.00));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/track/{trackingNo} - public tracking succeeds without authentication")
    void trackParcel_publicAccess() throws Exception {
        String trackingNo = "SD-20261107-XYZ789";
        Parcel parcel = new Parcel(
                trackingNo, UUID.randomUUID(), new BigDecimal("1.5"),
                "SMALL", new BigDecimal("470.00"), "Pickup address", "Dropoff address"
        );
        parcel.setCurrentStatus(ParcelStatus.IN_TRANSIT);

        List<ParcelStatusHistory> history = List.of(
                new ParcelStatusHistory(parcel.getId(), null, ParcelStatus.CREATED, null, "Created", null, null),
                new ParcelStatusHistory(parcel.getId(), ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, "Picked up", null, null),
                new ParcelStatusHistory(parcel.getId(), ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, null, "On the way", null, null)
        );

        ParcelDetailResponse response = ParcelDetailResponse.of(parcel, history);

        when(parcelService.getParcelDetails(trackingNo)).thenReturn(response);

        mvc.perform(get("/api/v1/parcels/track/" + trackingNo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcel.trackingNo").value(trackingNo))
                .andExpect(jsonPath("$.parcel.currentStatus").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.history.length()").value(3));
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status - invalid state transition returns 409 Conflict")
    void updateStatus_invalidTransitionReturns409() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        UpdateStatusRequest request = new UpdateStatusRequest(ParcelStatus.DELIVERED, "Direct to delivered", null, null);

        when(parcelService.updateStatus(eq(parcelId), any(), eq(courierId)))
                .thenThrow(new IllegalStateException("Invalid transition from CREATED to DELIVERED"));

        mvc.perform(patch("/api/v1/parcels/" + parcelId + "/status")
                        .with(jwt().jwt(j -> j.subject(courierId.toString()).claim("roles", List.of("COURIER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_COURIER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/assign - forbidden for CUSTOMER role (requires DISPATCHER/ADMIN)")
    void assignCourier_forbiddenForCustomer() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        AssignCourierRequest request = new AssignCourierRequest(UUID.randomUUID());

        mvc.perform(patch("/api/v1/parcels/" + parcelId + "/assign")
                        .with(jwt().jwt(j -> j.subject(customerId.toString()).claim("roles", List.of("CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
