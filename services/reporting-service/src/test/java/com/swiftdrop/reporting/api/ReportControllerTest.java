package com.swiftdrop.reporting.api;

import com.swiftdrop.common.security.CommonSecurityAutoConfiguration;
import com.swiftdrop.common.web.CommonWebAutoConfiguration;
import com.swiftdrop.reporting.config.SecurityConfig;
import com.swiftdrop.reporting.domain.DailyStats;
import com.swiftdrop.reporting.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@ImportAutoConfiguration({CommonSecurityAutoConfiguration.class, CommonWebAutoConfiguration.class})
@Import(SecurityConfig.class)
class ReportControllerTest {

    static final LocalDate D1 = LocalDate.of(2026, 11, 10);

    @Autowired MockMvc mvc;
    @MockitoBean ReportService reports;

    @Test
    void customersCouriersAndMerchantsCannotSeeReports() throws Exception {
        for (String role : List.of("CUSTOMER", "COURIER", "MERCHANT")) {
            mvc.perform(get("/api/v1/reports/summary").with(as(role))).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/reports/daily.csv").with(as(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(reports);
    }

    @Test
    void anonymousRequestsAre401() throws Exception {
        mvc.perform(get("/api/v1/reports/daily")).andExpect(status().isUnauthorized());
    }

    @Test
    void dispatchersAndAdminsGetTheDailyRows() throws Exception {
        when(reports.daily(D1, D1)).thenReturn(List.of(new DailyStats(D1, 4, 3, 1)));
        for (String role : List.of("DISPATCHER", "ADMIN")) {
            mvc.perform(get("/api/v1/reports/daily?from=2026-11-10&to=2026-11-10").with(as(role)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].day").value("2026-11-10"))
                    .andExpect(jsonPath("$[0].delivered").value(3));
        }
    }

    @Test
    void summaryReturnsTotalsAndRates() throws Exception {
        when(reports.summary(null, null)).thenReturn(new ReportService.Summary(D1, D1, 4, 3, 1, 4,
                new BigDecimal("75.0"), new BigDecimal("25.0")));
        mvc.perform(get("/api/v1/reports/summary").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successRate").value(75.0))
                .andExpect(jsonPath("$.failureRate").value(25.0));
    }

    @Test
    void csvDownloadHasAHeaderRowAndOneLinePerDay() throws Exception {
        when(reports.daily(any(), any())).thenReturn(List.of(new DailyStats(D1, 4, 3, 1), DailyStats.empty(D1.plusDays(1))));
        mvc.perform(get("/api/v1/reports/daily.csv?from=2026-11-10&to=2026-11-11").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"swiftdrop-deliveries-2026-11-10-to-2026-11-11.csv\""))
                .andExpect(content().string("date,created,delivered,failed,success_rate_percent\r\n"
                        + "2026-11-10,4,3,1,75.0\r\n2026-11-11,0,0,0,\r\n"));
    }

    @Test
    void badDatesAre400() throws Exception {
        mvc.perform(get("/api/v1/reports/daily?from=14-11-2026").with(as("ADMIN"))).andExpect(status().isBadRequest());
        when(reports.daily(any(), any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'"));
        mvc.perform(get("/api/v1/reports/daily?from=2026-11-12&to=2026-11-10").with(as("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must be on or before 'to'"));
    }

    @Test
    void otherPathsAreDenied() throws Exception {
        mvc.perform(get("/internal/anything").with(as("ADMIN"))).andExpect(status().isForbidden());
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString())).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
