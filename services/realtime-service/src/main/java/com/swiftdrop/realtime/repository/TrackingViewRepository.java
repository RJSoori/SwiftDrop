package com.swiftdrop.realtime.repository;

import com.swiftdrop.realtime.domain.TrackingRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TrackingViewRepository {

    private static final String FIND_BY_TRACKING_NO = """
            SELECT tracking_no, parcel_id, current_status, pickup_city, dropoff_city, timeline, updated_at
            FROM realtime.tracking_view
            WHERE tracking_no = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public TrackingViewRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TrackingRecord> findByTrackingNo(String trackingNo) {
        List<TrackingRecord> records = jdbcTemplate.query(FIND_BY_TRACKING_NO, (resultSet, rowNumber) ->
                new TrackingRecord(
                        resultSet.getString("tracking_no"),
                        resultSet.getObject("parcel_id", UUID.class),
                        resultSet.getString("current_status"),
                        resultSet.getString("pickup_city"),
                        resultSet.getString("dropoff_city"),
                        resultSet.getString("timeline"),
                        resultSet.getTimestamp("updated_at").toInstant()
                ), trackingNo);
        return records.stream().findFirst();
    }
}
