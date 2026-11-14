package com.swiftdrop.parcel.zone;

import com.swiftdrop.parcel.domain.PricingStrategyType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PostGIS reads and writes for zones and pricing rules. Plain SQL because JPA has no geometry type here; every value
 * is a bound parameter. PostGIS functions are schema-qualified (they live in public).
 */
@Repository
class ZoneJdbcRepository {

    /** Geometry for a GeoJSON parameter built by {@link GeoJsonPolygons}, never raw client input. */
    private static final String GEOM = "public.ST_SetSRID(public.ST_GeomFromGeoJSON(:geojson), 4326)";

    private static final String ZONE_COLUMNS =
            "id, name, active, created_at, public.ST_AsGeoJSON(boundary, 6) AS geojson";

    private static final String RULE_COLUMNS =
            "id, zone_id, strategy, base_fee, per_kg_fee, size_multiplier, valid_from, active, created_at";

    private final NamedParameterJdbcTemplate jdbc;

    ZoneJdbcRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- zones ----------

    /** The active zone covering the point (edges count). Shared edges resolve to the first zone by name. */
    Optional<ZoneRef> findActiveZoneCovering(double lat, double lng) {
        return jdbc.query("""
                        SELECT id, name FROM parcel.zones
                        WHERE active AND boundary IS NOT NULL
                          AND public.ST_Covers(boundary, public.ST_SetSRID(public.ST_MakePoint(:lng, :lat), 4326))
                        ORDER BY name
                        LIMIT 1""",
                new MapSqlParameterSource().addValue("lat", lat).addValue("lng", lng),
                (rs, i) -> new ZoneRef(rs.getObject("id", UUID.class), rs.getString("name"))).stream().findFirst();
    }

    Optional<ZoneRef> findActiveZone(UUID id) {
        return jdbc.query("SELECT id, name FROM parcel.zones WHERE id = :id AND active",
                new MapSqlParameterSource("id", id),
                (rs, i) -> new ZoneRef(rs.getObject("id", UUID.class), rs.getString("name"))).stream().findFirst();
    }

    List<ZoneRow> findZones(boolean activeOnly) {
        return jdbc.query("SELECT " + ZONE_COLUMNS + " FROM parcel.zones"
                        + (activeOnly ? " WHERE active" : "") + " ORDER BY name",
                new MapSqlParameterSource(), ZONE_ROW);
    }

    Optional<ZoneRow> findZone(UUID id) {
        return jdbc.query("SELECT " + ZONE_COLUMNS + " FROM parcel.zones WHERE id = :id",
                new MapSqlParameterSource("id", id), ZONE_ROW).stream().findFirst();
    }

    boolean nameTaken(String name, UUID exceptId) {
        Boolean taken = jdbc.queryForObject("""
                        SELECT EXISTS (SELECT 1 FROM parcel.zones
                                       WHERE lower(name) = lower(:name) AND id <> :exceptId)""",
                new MapSqlParameterSource().addValue("name", name).addValue("exceptId", orNone(exceptId)), Boolean.class);
        return Boolean.TRUE.equals(taken);
    }

    /** Whether PostGIS accepts the polygon, and why not if it does not. */
    GeometryCheck checkGeometry(String geojson) {
        return jdbc.queryForObject("""
                        SELECT public.ST_IsValid(g) AS valid, public.ST_IsValidReason(g) AS reason, public.ST_Area(g) AS area
                        FROM (SELECT %s AS g) AS input""".formatted(GEOM),
                new MapSqlParameterSource("geojson", geojson),
                (rs, i) -> new GeometryCheck(rs.getBoolean("valid"), rs.getString("reason"), rs.getDouble("area")));
    }

    /** Names of other active zones whose interior shares area with the polygon (touching edges are fine). */
    List<String> overlappingActiveZones(String geojson, UUID exceptId) {
        return jdbc.queryForList("""
                        SELECT name FROM parcel.zones
                        WHERE active AND boundary IS NOT NULL
                          AND id <> :exceptId
                          AND public.ST_Relate(boundary, %s, '2********')
                        ORDER BY name""".formatted(GEOM),
                new MapSqlParameterSource().addValue("geojson", geojson).addValue("exceptId", orNone(exceptId)), String.class);
    }

    /** Held until the transaction ends; writers queue, readers (quotes) are not blocked. */
    void lockForWrite() {
        jdbc.getJdbcTemplate().execute("LOCK TABLE parcel.zones IN SHARE ROW EXCLUSIVE MODE");
    }

    void insertZone(UUID id, String name, String geojson) {
        jdbc.update("INSERT INTO parcel.zones (id, name, boundary, active) VALUES (:id, :name, " + GEOM + ", TRUE)",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("geojson", geojson));
    }

    int updateZone(UUID id, String name, String geojson) {
        return jdbc.update("UPDATE parcel.zones SET name = :name, boundary = " + GEOM + " WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("name", name).addValue("geojson", geojson));
    }

    int setZoneActive(UUID id, boolean active) {
        return jdbc.update("UPDATE parcel.zones SET active = :active WHERE id = :id",
                new MapSqlParameterSource().addValue("id", id).addValue("active", active));
    }

    // ---------- pricing rules ----------

    Optional<PricingRuleView> findActiveRule(UUID zoneId, Instant at) {
        return jdbc.query("SELECT " + RULE_COLUMNS + """
                         FROM parcel.pricing_rules
                        WHERE zone_id = :zoneId AND active AND valid_from <= :at
                        ORDER BY valid_from DESC, created_at DESC
                        LIMIT 1""",
                new MapSqlParameterSource().addValue("zoneId", zoneId).addValue("at", Timestamp.from(at)),
                RULE_ROW).stream().findFirst();
    }

    List<PricingRuleView> findRules(UUID zoneId) {
        return jdbc.query("SELECT " + RULE_COLUMNS
                        + " FROM parcel.pricing_rules WHERE zone_id = :zoneId ORDER BY valid_from DESC, created_at DESC",
                new MapSqlParameterSource("zoneId", zoneId), RULE_ROW);
    }

    Optional<PricingRuleView> findRule(UUID zoneId, UUID ruleId) {
        return jdbc.query("SELECT " + RULE_COLUMNS + " FROM parcel.pricing_rules WHERE id = :id AND zone_id = :zoneId",
                new MapSqlParameterSource().addValue("id", ruleId).addValue("zoneId", zoneId), RULE_ROW)
                .stream().findFirst();
    }

    void insertRule(UUID id, UUID zoneId, PricingStrategyType strategy, BigDecimal baseFee, BigDecimal perKgFee,
                    BigDecimal sizeMultiplier, Instant validFrom) {
        jdbc.update("""
                        INSERT INTO parcel.pricing_rules
                            (id, zone_id, strategy, base_fee, per_kg_fee, size_multiplier, valid_from, active)
                        VALUES (:id, :zoneId, :strategy, :baseFee, :perKgFee, :sizeMultiplier, :validFrom, TRUE)""",
                new MapSqlParameterSource()
                        .addValue("id", id).addValue("zoneId", zoneId).addValue("strategy", strategy.name())
                        .addValue("baseFee", baseFee).addValue("perKgFee", perKgFee)
                        .addValue("sizeMultiplier", sizeMultiplier).addValue("validFrom", Timestamp.from(validFrom)));
    }

    int deactivateRule(UUID zoneId, UUID ruleId) {
        return jdbc.update("UPDATE parcel.pricing_rules SET active = FALSE WHERE id = :id AND zone_id = :zoneId",
                new MapSqlParameterSource().addValue("id", ruleId).addValue("zoneId", zoneId));
    }

    /** No zone has the nil UUID, so "id <> nil" matches every row. Avoids binding a typeless NULL. */
    private static UUID orNone(UUID id) {
        return id == null ? NO_ZONE : id;
    }

    private static final UUID NO_ZONE = new UUID(0L, 0L);

    record GeometryCheck(boolean valid, String reason, double area) {
    }

    private static final RowMapper<ZoneRow> ZONE_ROW = (rs, i) -> new ZoneRow(
            rs.getObject("id", UUID.class), rs.getString("name"), rs.getBoolean("active"),
            instant(rs, "created_at"), rs.getString("geojson"));

    private static final RowMapper<PricingRuleView> RULE_ROW = (rs, i) -> new PricingRuleView(
            rs.getObject("id", UUID.class), rs.getObject("zone_id", UUID.class),
            PricingStrategyType.valueOf(rs.getString("strategy")), rs.getBigDecimal("base_fee"),
            rs.getBigDecimal("per_kg_fee"), rs.getBigDecimal("size_multiplier"), instant(rs, "valid_from"),
            rs.getBoolean("active"), instant(rs, "created_at"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
