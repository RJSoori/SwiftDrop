package com.swiftdrop.reporting.repository;

import com.swiftdrop.reporting.domain.DailyStats;
import com.swiftdrop.reporting.domain.StatsCounter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Atomic counters and the processed-events guard. SQL so that both are single race-free statements. */
@Repository
public class DailyStatsRepository {

    private final JdbcTemplate jdbc;

    public DailyStatsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** True the first time this eventId is seen; false for a duplicate (also across replicas). */
    public boolean markProcessed(UUID eventId, String eventType) {
        return jdbc.update("INSERT INTO reporting.processed_events (event_id, event_type) VALUES (?, ?) ON CONFLICT DO NOTHING",
                eventId, eventType) == 1;
    }

    public void increment(LocalDate day, StatsCounter counter) {
        String column = counter.column();
        jdbc.update("INSERT INTO reporting.daily_stats (day, " + column + ") VALUES (?, 1) "
                + "ON CONFLICT (day) DO UPDATE SET " + column + " = reporting.daily_stats." + column + " + 1, updated_at = now()",
                Date.valueOf(day));
    }

    /** Days with at least one event between the two dates, inclusive, oldest first. */
    public List<DailyStats> findBetween(LocalDate from, LocalDate to) {
        return jdbc.query("""
                        SELECT day, created_count, delivered_count, failed_count FROM reporting.daily_stats
                        WHERE day BETWEEN ? AND ? ORDER BY day""",
                (rs, i) -> new DailyStats(rs.getDate("day").toLocalDate(), rs.getLong("created_count"),
                        rs.getLong("delivered_count"), rs.getLong("failed_count")),
                Date.valueOf(from), Date.valueOf(to));
    }
}
