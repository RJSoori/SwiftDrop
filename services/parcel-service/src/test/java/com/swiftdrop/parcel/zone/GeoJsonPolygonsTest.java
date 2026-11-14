package com.swiftdrop.parcel.zone;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.parcel.zone.ZoneDtos.GeoJsonPolygon;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoJsonPolygonsTest {

    @Test
    void anOpenRingIsClosedAndRounded() {
        List<List<Double>> ring = GeoJsonPolygons.normalisedRing(polygon(
                List.of(79.1, 6.1), List.of(79.2, 6.1), List.of(79.2000004, 6.2)));
        assertThat(ring).hasSize(4);
        assertThat(ring.getLast()).isEqualTo(ring.getFirst());
        assertThat(ring.get(2)).containsExactly(79.2, 6.2);
    }

    @Test
    void theGeoJsonKeepsOnlyTypeAndCoordinates() throws Exception {
        String geojson = GeoJsonPolygons.toGeoJson(
                GeoJsonPolygons.normalisedRing(polygon(List.of(79.1, 6.1), List.of(79.2, 6.1), List.of(79.2, 6.2))),
                new ObjectMapper());
        assertThat(new ObjectMapper().readTree(geojson).fieldNames()).toIterable().containsExactly("type", "coordinates");
    }

    @Test
    void rejectsPointsOutsideSriLanka() {
        assertRejected(polygon(List.of(73.0, 4.0), List.of(73.5, 4.0), List.of(73.5, 4.5)), "inside Sri Lanka");
    }

    @Test
    void rejectsTooFewPoints() {
        assertRejected(polygon(List.of(79.1, 6.1), List.of(79.2, 6.1), List.of(79.1, 6.1)), "three distinct points");
    }

    @Test
    void rejectsPositionsThatAreNotPairs() {
        assertRejected(polygon(List.of(79.1, 6.1, 0.0), List.of(79.2, 6.1), List.of(79.2, 6.2)), "pair");
        assertRejected(polygon(List.of(Double.NaN, 6.1), List.of(79.2, 6.1), List.of(79.2, 6.2)), "pair");
    }

    @Test
    void rejectsHolesAndOtherTypes() {
        List<List<Double>> ring = List.of(List.of(79.1, 6.1), List.of(79.2, 6.1), List.of(79.2, 6.2));
        assertRejected(new GeoJsonPolygon("Polygon", List.of(ring, ring)), "single outline");
        assertRejected(new GeoJsonPolygon("MultiPolygon", List.of(ring)), "single outline");
    }

    @Test
    void rejectsHugeOutlines() {
        List<Double>[] points = new List[GeoJsonPolygons.MAX_POSITIONS + 1];
        Arrays.fill(points, List.of(79.1, 6.1));
        assertRejected(polygon(points), "at most");
    }

    @SafeVarargs
    private static GeoJsonPolygon polygon(List<Double>... points) {
        return new GeoJsonPolygon("Polygon", List.of(List.of(points)));
    }

    private static void assertRejected(GeoJsonPolygon polygon, String message) {
        assertThatThrownBy(() -> GeoJsonPolygons.normalisedRing(polygon))
                .isInstanceOf(ZoneValidationException.class)
                .satisfies(e -> assertThat(((ZoneValidationException) e).getField()).isEqualTo("boundary"))
                .satisfies(e -> assertThat(((ZoneValidationException) e).getBody().getDetail()).contains(message));
    }
}
