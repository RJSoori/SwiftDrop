package com.swiftdrop.parcel.zone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.zone.ZoneDtos.GeoJsonPolygon;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Checks the shape of a client polygon and rebuilds it as a clean GeoJSON string for PostGIS. Only the type and the
 * rounded coordinates are kept, so no client-supplied keys (crs and so on) reach the database.
 */
final class GeoJsonPolygons {

    static final String FIELD = "boundary";

    /** A generous box around Sri Lanka and its waters. A zone outside it is a mistake, not a delivery area. */
    static final double MIN_LNG = 79.0;
    static final double MAX_LNG = 82.5;
    static final double MIN_LAT = 5.5;
    static final double MAX_LAT = 10.5;

    static final int MAX_POSITIONS = 500;

    private GeoJsonPolygons() {
    }

    /** The validated ring, closed, with coordinates rounded to 6 decimals (about 10 cm). */
    static List<List<Double>> normalisedRing(GeoJsonPolygon polygon) {
        if (polygon == null || !"Polygon".equals(polygon.type()) || polygon.coordinates() == null
                || polygon.coordinates().size() != 1) {
            throw invalid("Draw one polygon with a single outline");
        }
        List<List<Double>> ring = polygon.coordinates().getFirst();
        if (ring == null || ring.size() > MAX_POSITIONS) {
            throw invalid("A zone outline can have at most " + MAX_POSITIONS + " points");
        }
        List<List<Double>> out = new ArrayList<>(ring.size() + 1);
        for (List<Double> position : ring) {
            if (position == null || position.size() != 2 || position.stream().anyMatch(Objects::isNull)) {
                throw invalid("Every point must be a [longitude, latitude] pair");
            }
            double lng = position.get(0);
            double lat = position.get(1);
            if (!Double.isFinite(lng) || !Double.isFinite(lat)) {
                throw invalid("Every point must be a [longitude, latitude] pair");
            }
            if (lng < MIN_LNG || lng > MAX_LNG || lat < MIN_LAT || lat > MAX_LAT) {
                throw invalid("The zone must be inside Sri Lanka");
            }
            out.add(List.of(round(lng), round(lat)));
        }
        if (!out.isEmpty() && !out.getFirst().equals(out.getLast())) {
            out.add(out.getFirst()); // close the ring for the client
        }
        if (out.stream().distinct().count() < 3 || out.size() < 4) {
            throw invalid("A zone needs at least three distinct points");
        }
        return out;
    }

    static String toGeoJson(List<List<Double>> ring, ObjectMapper json) {
        try {
            return json.writeValueAsString(new GeoJsonPolygon("Polygon", List.of(ring)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write a polygon", e);
        }
    }

    static GeoJsonPolygon fromGeoJson(String geojson, ObjectMapper json) {
        if (geojson == null) {
            return null;
        }
        try {
            return json.readValue(geojson, GeoJsonPolygon.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored zone boundary is not GeoJSON", e);
        }
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private static ZoneValidationException invalid(String message) {
        return ZoneValidationException.badRequest(FIELD, message);
    }
}
