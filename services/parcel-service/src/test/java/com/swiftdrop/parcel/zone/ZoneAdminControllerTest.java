package com.swiftdrop.parcel.zone;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.parcel.config.SecurityConfig;
import com.swiftdrop.parcel.zone.ZoneDtos.ZoneSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Roles and request validation for the zone APIs; the service is mocked. */
@WebMvcTest({ZoneAdminController.class, ZoneController.class})
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
@Import({SecurityConfig.class, ZoneAdminSecurityConfig.class})
class ZoneAdminControllerTest {

    private static final String SQUARE =
            "{\"type\": \"Polygon\", \"coordinates\": [[[79.1, 6.1], [79.2, 6.1], [79.2, 6.2], [79.1, 6.1]]]}";

    @Autowired MockMvc mvc;
    @MockitoBean ZoneAdminService zones;

    @Test
    void customersAndDispatchersCannotManageZones() throws Exception {
        for (String role : List.of("CUSTOMER", "DISPATCHER", "COURIER", "MERCHANT")) {
            mvc.perform(get("/api/v1/admin/zones").with(as(role))).andExpect(status().isForbidden());
            mvc.perform(post("/api/v1/admin/zones").with(as(role)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"X\", \"boundary\": " + SQUARE + "}")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(zones);
    }

    @Test
    void anonymousRequestsAre401() throws Exception {
        mvc.perform(get("/api/v1/admin/zones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/zones")).andExpect(status().isUnauthorized());
    }

    @Test
    void anyLoggedInUserReadsZoneOutlines() throws Exception {
        when(zones.activeZones()).thenReturn(List.of(new ZoneSummary(UUID.randomUUID(), "Kandy", null)));
        mvc.perform(get("/api/v1/zones").with(as("COURIER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kandy"));
    }

    @Test
    void negativeFeesAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/admin/zones/" + UUID.randomUUID() + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\": \"FLAT_RATE\", \"baseFee\": -1, \"perKgFee\": 10, \"sizeMultiplier\": 1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("baseFee"));
        verifyNoInteractions(zones);
    }

    @Test
    void missingRuleFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/v1/admin/zones/" + UUID.randomUUID() + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"strategy\": \"FLAT_RATE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void sizeMultiplierMustBeSensible() throws Exception {
        mvc.perform(post("/api/v1/admin/zones/" + UUID.randomUUID() + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\": \"FLAT_RATE\", \"baseFee\": 1, \"perKgFee\": 1, \"sizeMultiplier\": 9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sizeMultiplier"));
    }

    @Test
    void zoneNeedsANameAndAPolygon() throws Exception {
        mvc.perform(post("/api/v1/admin/zones").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"<script>\", \"boundary\": {\"type\": \"Point\", \"coordinates\": []}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
        verifyNoInteractions(zones);
    }

    @Test
    void serviceRuleErrorsKeepTheirFieldAndStatus() throws Exception {
        when(zones.create(any(), any())).thenThrow(ZoneValidationException.badRequest("boundary", "The outline overlaps Kandy"));
        mvc.perform(post("/api/v1/admin/zones").with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Hill Country\", \"boundary\": " + SQUARE + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The outline overlaps Kandy"))
                .andExpect(jsonPath("$.errors[0].field").value("boundary"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
