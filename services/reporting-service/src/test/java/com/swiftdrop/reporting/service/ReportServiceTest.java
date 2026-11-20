package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.domain.DailyStats;
import com.swiftdrop.reporting.repository.DailyStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    static final LocalDate D1 = LocalDate.of(2026, 11, 10);
    static final LocalDate D3 = LocalDate.of(2026, 11, 12);

    @Mock DailyStatsRepository stats;

    ReportService service() {
        // 20:00 UTC on the 13th is already the 14th in Sri Lanka
        return new ReportService(stats, Clock.fixed(Instant.parse("2026-11-13T20:00:00Z"), ReportService.BUSINESS_ZONE));
    }

    @Test
    void dailyFillsDaysWithoutEventsWithZeros() {
        when(stats.findBetween(D1, D3)).thenReturn(List.of(new DailyStats(D1.plusDays(1), 4, 3, 1)));
        assertThat(service().daily(D1, D3)).containsExactly(
                DailyStats.empty(D1), new DailyStats(D1.plusDays(1), 4, 3, 1), DailyStats.empty(D3));
    }

    @Test
    void summaryAddsUpTheRangeAndComputesRates() {
        when(stats.findBetween(D1, D3)).thenReturn(List.of(new DailyStats(D1, 5, 2, 1), new DailyStats(D3, 3, 4, 2)));
        ReportService.Summary summary = service().summary(D1, D3);
        assertThat(summary.created()).isEqualTo(8);
        assertThat(summary.delivered()).isEqualTo(6);
        assertThat(summary.failed()).isEqualTo(3);
        assertThat(summary.completed()).isEqualTo(9);
        assertThat(summary.successRate()).isEqualByComparingTo("66.7");
        assertThat(summary.failureRate()).isEqualByComparingTo("33.3");
    }

    @Test
    void ratesAreNullWhenNothingWasCompleted() {
        when(stats.findBetween(D1, D3)).thenReturn(List.of(new DailyStats(D1, 5, 0, 0)));
        assertThat(service().summary(D1, D3).successRate()).isNull();
        assertThat(ReportService.rate(0, 0)).isNull();
    }

    @Test
    void theDefaultRangeIsTheLast30SriLankanDays() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        assertThat(service().today()).isEqualTo(today);
        service().summary(null, null);
        verify(stats).findBetween(today.minusDays(29), today);
    }

    @Test
    void reversedRangesAreRejected() {
        assertThatThrownBy(() -> service().daily(D3, D1))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");
    }

    @Test
    void rangesLongerThanAYearAreRejected() {
        assertThatThrownBy(() -> service().daily(D1.minusDays(366), D1))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("366");
        assertThat(service().daily(D1.minusDays(365), D1)).hasSize(366);
    }
}
