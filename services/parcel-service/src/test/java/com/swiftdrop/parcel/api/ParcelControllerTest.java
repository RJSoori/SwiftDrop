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

@WebMvcTest({ParcelController.class, QuoteController.class})
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
        CreateParcelRequest request = validBooking(new BigDecimal("2.0"));

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

    private static CreateParcelRequest validBooking(BigDecimal weightKg) {
        return new CreateParcelRequest("123 Galle Rd, Colombo", "45 Main St, Kandy", weightKg,
                new BigDecimal("20.0"), new BigDecimal("15.0"), new BigDecimal("10.0"), "MEDIUM",
                PricingStrategyType.WEIGHT_TIERED, null, null, null, "Colombo", null, null, "Kandy", null, null,
                "Nimal Perera", "0771234567", "STANDARD");
    }

    @Test
    @DisplayName("POST /api/v1/parcels - an empty body or a bad weight is a 400 problem and nothing is booked")
    void createParcel_invalidBodyIs400() throws Exception {
        UUID customerId = UUID.randomUUID();
        for (String body : List.of("{}",
                objectMapper.writeValueAsString(validBooking(new BigDecimal("-5"))),
                objectMapper.writeValueAsString(validBooking(new BigDecimal("101"))))) {
            mvc.perform(post("/api/v1/parcels").with(as(customerId, "CUSTOMER"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                            .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(400));
        }
        mvc.perform(post("/api/v1/parcels").with(as(customerId, "CUSTOMER")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pickupAddress\":\"a\",\"dropoffAddress\":\"b\",\"weightKg\":1,\"lengthCm\":-1,"
                                + "\"recipientName\":\"n\",\"recipientPhone\":\"p\"}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verify(parcelService, org.mockito.Mockito.never()).createParcel(any(), any());
    }

    @Test
    @DisplayName("Quotes on /api/v1/quotes and /api/v1/parcels/quote - an empty body or a negative weight is a 400 problem")
    void quote_invalidBodyIs400() throws Exception {
        for (String path : List.of("/api/v1/quotes", "/api/v1/parcels/quote")) {
            for (String body : List.of("{}", "{\"weightKg\":-1}", "{\"weightKg\":2,\"widthCm\":0}")) {
                mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.status").value(400));
            }
        }
        org.mockito.Mockito.verify(parcelService, org.mockito.Mockito.never()).calculateQuote(any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(UUID userId, String role) {
        return jwt().jwt(j -> j.subject(userId.toString()).claim("roles", List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ParcelDetailResponse stubParcel(UUID parcelId, UUID ownerId) {
        Parcel parcel = new Parcel("SD-20261107-XYZ789", ownerId, new BigDecimal("1.5"),
                "SMALL", new BigDecimal("470.00"), "Pickup address", "Dropoff address");
        ParcelDetailResponse details = ParcelDetailResponse.of(parcel, List.of(
                new ParcelStatusHistory(parcel.getId(), null, ParcelStatus.CREATED, null, "Created", null, null)));
        when(parcelService.getParcelById(parcelId)).thenReturn(ParcelResponse.from(parcel));
        when(parcelService.getParcelDetails("SD-20261107-XYZ789")).thenReturn(details);
        return details;
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} - the owning customer sees the parcel")
    void getParcel_ownerAllowed() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubParcel(parcelId, ownerId);
        when(parcelService.canAccessParcel(parcelId, ownerId, "CUSTOMER")).thenReturn(true);

        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(ownerId, "CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcel.trackingNo").value("SD-20261107-XYZ789"))
                .andExpect(jsonPath("$.history.length()").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} - another customer gets 404 and no data")
    void getParcel_otherCustomerGets404() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID otherCustomer = UUID.randomUUID();
        stubParcel(parcelId, UUID.randomUUID());
        when(parcelService.canAccessParcel(parcelId, otherCustomer, "CUSTOMER")).thenReturn(false);

        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(otherCustomer, "CUSTOMER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.parcel").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} - a courier or merchant who is not the owning customer gets 404")
    void getParcel_otherRolesGet404() throws Exception {
        UUID parcelId = UUID.randomUUID();
        stubParcel(parcelId, UUID.randomUUID());

        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(UUID.randomUUID(), "COURIER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(UUID.randomUUID(), "MERCHANT")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/{id} - dispatcher and admin can read any parcel")
    void getParcel_staffAllowed() throws Exception {
        UUID parcelId = UUID.randomUUID();
        stubParcel(parcelId, UUID.randomUUID());

        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(UUID.randomUUID(), "DISPATCHER")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/parcels/" + parcelId).with(as(UUID.randomUUID(), "ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/track/{no} - removed; public tracking lives at /api/v1/tracking")
    void trackEndpointIsGone() throws Exception {
        mvc.perform(get("/api/v1/parcels/track/SD-20261107-XYZ789")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/parcels/track/SD-20261107-XYZ789").with(as(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/{id} - the courier themselves, a dispatcher or an admin")
    void courierParcels_selfAndStaffAllowed() throws Exception {
        UUID courierId = UUID.randomUUID();
        when(parcelService.getParcelsByCourier(courierId)).thenReturn(List.of());

        mvc.perform(get("/api/v1/parcels/courier/" + courierId).with(as(courierId, "COURIER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/parcels/courier/" + courierId).with(as(UUID.randomUUID(), "DISPATCHER"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/parcels/courier/" + courierId).with(as(UUID.randomUUID(), "ADMIN"))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/{id} - another courier or a customer gets 403")
    void courierParcels_othersForbidden() throws Exception {
        UUID courierId = UUID.randomUUID();

        mvc.perform(get("/api/v1/parcels/courier/" + courierId).with(as(UUID.randomUUID(), "COURIER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/parcels/courier/" + courierId).with(as(UUID.randomUUID(), "CUSTOMER")))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(parcelService, org.mockito.Mockito.never()).getParcelsByCourier(any());
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status - a customer or courier cannot change status")
    void updateStatus_customerAndCourierForbidden() throws Exception {
        UUID parcelId = UUID.randomUUID();
        String body = objectMapper.writeValueAsString(new UpdateStatusRequest(ParcelStatus.IN_TRANSIT, "moved", null, null));

        mvc.perform(patch("/api/v1/parcels/" + parcelId + "/status").with(as(UUID.randomUUID(), "CUSTOMER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/parcels/" + parcelId + "/status").with(as(UUID.randomUUID(), "COURIER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verify(parcelService, org.mockito.Mockito.never()).updateStatus(any(), any(), any());
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status - invalid state transition returns 409 Conflict")
    void updateStatus_invalidTransitionReturns409() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID dispatcherId = UUID.randomUUID();
        UpdateStatusRequest request = new UpdateStatusRequest(ParcelStatus.DELIVERED, "Direct to delivered", null, null);

        when(parcelService.updateStatus(eq(parcelId), any(), eq(dispatcherId)))
                .thenThrow(new IllegalStateException("Invalid transition from CREATED to DELIVERED"));

        mvc.perform(patch("/api/v1/parcels/" + parcelId + "/status")
                        .with(as(dispatcherId, "DISPATCHER"))
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
