package com.swiftdrop.realtime.repository;

import com.swiftdrop.realtime.domain.TrackingRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TrackingViewRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrackingViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TrackingRecord> findByTrackingNo(String trackingNo) {
        List<TrackingRecord> records = jdbcTemplate.query("""
                SELECT tracking_no, parcel_id, current_status, pickup_city, dropoff_city, timeline, updated_at,
                       aggregate_version
                FROM realtime.tracking_view
                WHERE tracking_no = ?
                """, this::mapRecord, trackingNo);
        return records.stream().findFirst();
    }

    public boolean markEventProcessed(UUID eventId) {
        return jdbcTemplate.update("""
                INSERT INTO realtime.processed_events (event_id)
                VALUES (?)
                ON CONFLICT (event_id) DO NOTHING
                """, eventId) == 1;
    }

    public boolean createTrackingView(String trackingNo, UUID parcelId, String pickupCity, String dropoffCity,
                                      String timelineJson, java.time.Instant occurredAt) {
        return jdbcTemplate.update("""
                INSERT INTO realtime.tracking_view
                    (tracking_no, parcel_id, current_status, pickup_city, dropoff_city, timeline, updated_at)
                VALUES (?, ?, 'CREATED', ?, ?, ?::jsonb, ?)
                ON CONFLICT (tracking_no) DO NOTHING
                """, trackingNo, parcelId, pickupCity, dropoffCity, timelineJson,
                java.sql.Timestamp.from(occurredAt)) == 1;
    }

    public boolean updateStatus(String trackingNo, String status, String timelineEntryJson,
                                java.time.Instant occurredAt, Long aggregateVersion) {
        return jdbcTemplate.update("""
                UPDATE realtime.tracking_view
                SET current_status = ?,
                    timeline = timeline || ?::jsonb,
                    updated_at = ?,
                    aggregate_version = CASE WHEN ?::bigint IS NULL THEN aggregate_version
                                             ELSE GREATEST(aggregate_version, ?) END
                WHERE tracking_no = ?
                  AND (?::bigint IS NULL OR ? > aggregate_version)
                """, status, timelineEntryJson, java.sql.Timestamp.from(occurredAt),
                aggregateVersion, aggregateVersion, trackingNo,
                aggregateVersion, aggregateVersion) == 1;
    }

    private TrackingRecord mapRecord(java.sql.ResultSet resultSet, int rowNumber) throws java.sql.SQLException {
        return new TrackingRecord(
                resultSet.getString("tracking_no"),
                resultSet.getObject("parcel_id", UUID.class),
                resultSet.getString("current_status"),
                resultSet.getString("pickup_city"),
                resultSet.getString("dropoff_city"),
                resultSet.getString("timeline"),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("aggregate_version")
        );
    }
}
