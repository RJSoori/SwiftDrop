package com.swiftdrop.reporting.service;

import com.swiftdrop.reporting.domain.DailyStats;
import com.swiftdrop.reporting.repository.DailyStatsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UC-11: deliveries per day and success / failure rates for a date range. */
@Service
public class ReportService {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");
    public static final int MAX_DAYS = 366;
    public static final int DEFAULT_DAYS = 30;

    private final DailyStatsRepository stats;
    private final Clock clock;

    @Autowired
    public ReportService(DailyStatsRepository stats) {
        this(stats, Clock.system(BUSINESS_ZONE));
    }

    ReportService(DailyStatsRepository stats, Clock clock) {
        this.stats = stats;
        this.clock = clock;
    }

    /** One row per day in the range, including days with no events. */
    @Transactional(readOnly = true)
    public List<DailyStats> daily(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        Map<LocalDate, DailyStats> byDay = stats.findBetween(range.from(), range.to()).stream()
                .collect(Collectors.toMap(DailyStats::day, Function.identity()));
        List<DailyStats> days = new ArrayList<>();
        for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
            days.add(byDay.getOrDefault(day, DailyStats.empty(day)));
        }
        return days;
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        DateRange range = range(from, to);
        long created = 0;
        long delivered = 0;
        long failed = 0;
        for (DailyStats day : stats.findBetween(range.from(), range.to())) {
            created += day.created();
            delivered += day.delivered();
            failed += day.failed();
        }
        return new Summary(range.from(), range.to(), created, delivered, failed, delivered + failed,
                rate(delivered, delivered + failed), rate(failed, delivered + failed));
    }

    /** Today's date in Sri Lanka. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(BUSINESS_ZONE));
    }

    /** Percentage with one decimal, or null when there is nothing to divide by. */
    public static BigDecimal rate(long part, long whole) {
        if (whole == 0) {
            return null;
        }
        return BigDecimal.valueOf(part * 100L).divide(BigDecimal.valueOf(whole), 1, RoundingMode.HALF_UP);
    }

    /** Defaults to the last {@value #DEFAULT_DAYS} days ending today; rejects reversed or over-long ranges. */
    DateRange range(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : today();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must be on or before 'to'");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A report can cover at most " + MAX_DAYS + " days");
        }
        return new DateRange(start, end);
    }

    record DateRange(LocalDate from, LocalDate to) {
    }

    /**
     * Totals for a range. {@code completed} = delivered + failed; the rates are percentages of completed
     * deliveries (success = Delivered, failure = Failed) and null when nothing was completed.
     */
    public record Summary(LocalDate from, LocalDate to, long created, long delivered, long failed, long completed,
                          BigDecimal successRate, BigDecimal failureRate) {
    }
}
