package com.swiftdrop.parcel.zone;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swiftdrop.testsupport.PostgisContainerConfig;
import com.swiftdrop.testsupport.RabbitContainerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zones, pricing rules and zone-priced quotes against a real PostGIS database (Flyway migrations included) and
 * RabbitMQ. Each test that writes uses its own patch of sea west of the island, so tests do not affect each other
 * or the seeded zones.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({PostgisContainerConfig.class, RabbitContainerConfig.class})
class ZoneIntegrationTest {

    static final String COLOMBO = "11111111-1111-1111-1111-111111111101";
    static final String KANDY = "11111111-1111-1111-1111-111111111102";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ZoneLookupService lookup;
    @Autowired PricingRuleService rules;

    // ---------- seeded zones ----------

    /** Every city offered by the booking form, the AI assistant, the demo store and the demo seed. */
    @ParameterizedTest(name = "{0} is in {3}")
    @CsvSource({
            "Colombo, 6.9271, 79.8612, Colombo", "Dehiwala-Mount Lavinia, 6.8301, 79.8801, Colombo",
            "Moratuwa, 6.7730, 79.8816, Colombo", "Kotte, 6.9016, 79.9077, Colombo",
            "Negombo, 7.2088, 79.8358, Gampaha", "Gampaha, 7.0840, 79.9939, Gampaha",
            "Kalutara, 6.5854, 79.9607, Kalutara", "Kandy, 7.2906, 80.6337, Kandy",
            "Matale, 7.4675, 80.6234, Kandy", "Nuwara Eliya, 6.9497, 80.7891, Kandy",
            "Galle, 6.0535, 80.2210, Galle", "Matara, 5.9549, 80.5550, Galle",
            "Hambantota, 6.1429, 81.1212, Galle", "Jaffna, 9.6615, 80.0255, Jaffna",
            "Vavuniya, 8.7514, 80.4971, Jaffna", "Mannar, 8.9810, 79.9044, Jaffna",
            "Trincomalee, 8.5874, 81.2152, Batticaloa", "Batticaloa, 7.7310, 81.6747, Batticaloa",
            "Ampara, 7.2975, 81.6724, Batticaloa", "Kurunegala, 7.4863, 80.3623, Kurunegala",
            "Puttalam, 8.0362, 79.8283, Kurunegala", "Chilaw, 7.5758, 79.7953, Kurunegala",
            "Anuradhapura, 8.3114, 80.4037, Anuradhapura", "Polonnaruwa, 7.9403, 81.0188, Anuradhapura",
            "Badulla, 6.9934, 81.0550, Badulla", "Ratnapura, 6.6828, 80.4036, Ratnapura",
            "Kegalle, 7.2513, 80.3464, Ratnapura"})
    void everyOfferedCityIsInExactlyTheExpectedZone(String city, double lat, double lng, String zone) {
        assertThat(lookup.findZone(lat, lng)).map(ZoneRef::name).contains(zone);
    }

    @Test
    void pointsInTheSeaAreInNoZone() {
        assertThat(lookup.findZone(6.9, 79.0)).isEmpty();
        assertThat(lookup.findZone(5.5, 80.5)).isEmpty();
    }

    @Test
    void seededZonesShareEdgesButNoArea() {
        Integer overlapping = jdbc.queryForObject("""
                SELECT count(*) FROM parcel.zones a JOIN parcel.zones b ON a.id < b.id
                WHERE a.active AND b.active AND public.ST_Relate(a.boundary, b.boundary, '2********')""", Integer.class);
        assertThat(overlapping).isZero();
    }

    @Test
    void textAndGeometryBoundariesStayInSync() {
        String wkt = jdbc.queryForObject(
                "SELECT boundary_wkt FROM parcel.zones WHERE id = CAST(? AS uuid)", String.class, KANDY);
        assertThat(wkt).startsWith("POLYGON((80.5 6.85");
    }

    @Test
    void everySeededZoneHasARuleInForce() {
        assertThat(rules.activeRuleFor(UUID.fromString(KANDY), LocalDate.now(ZoneId.of("Asia/Colombo"))))
                .get().satisfies(r -> assertThat(r.baseFee()).isEqualByComparingTo("450.00"));
        Integer unpriced = jdbc.queryForObject("""
                SELECT count(*) FROM parcel.zones z
                WHERE CAST(z.id AS text) LIKE '11111111-%' AND NOT EXISTS (
                    SELECT 1 FROM parcel.pricing_rules r WHERE r.zone_id = z.id AND r.active AND r.valid_from <= now())""",
                Integer.class);
        assertThat(unpriced).isZero();
    }

    // ---------- quotes priced by zone ----------

    @Test
    void quoteToKandyUsesTheKandyRule() throws Exception {
        // Default box 20x15x10 cm = 0.6 kg volumetric, so 2 kg is charged: 450 + 2 x 100
        quote(colomboToKandy("2", null, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.feeLkr").value(650.00))
                .andExpect(jsonPath("$.pickupZoneName").value("Colombo"))
                .andExpect(jsonPath("$.dropoffZoneName").value("Kandy"))
                .andExpect(jsonPath("$.dropoffZoneId").value(KANDY))
                .andExpect(jsonPath("$.baseFeeLkr").value(450.00))
                .andExpect(jsonPath("$.strategy").value("WEIGHT_TIERED"));
    }

    @Test
    void largeParcelsPayTheZoneSizeMultiplier() throws Exception {
        quote(colomboToKandy("2", null, "LARGE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.feeLkr").value(812.50)); // 650 x 1.25
    }

    @Test
    void expressServiceKeepsTheExpressStrategyWithZoneRates() throws Exception {
        quote(colomboToKandy("2", "EXPRESS", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("EXPRESS"))
                .andExpect(jsonPath("$.feeLkr").value(750.00)); // 450 + 2 x 100 x 1.5
    }

    @Test
    void quoteWithoutLocationsKeepsTheDefaultRates() throws Exception {
        quote("{\"weightKg\": 2}").andExpect(status().isOk())
                .andExpect(jsonPath("$.feeLkr").value(510.00)) // 350 + 2 x 80
                .andExpect(jsonPath("$.dropoffZoneId").doesNotExist());
    }

    @Test
    void quoteFromTheSeaIsRejectedWithAFieldError() throws Exception {
        quote("""
                {"weightKg": 2, "pickupLat": 6.9, "pickupLng": 79.0, "dropoffLat": 7.2906, "dropoffLng": 80.6337}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pickup"))
                .andExpect(jsonPath("$.detail").value("Pickup location is outside SwiftDrop's delivery zones"));
    }

    @Test
    void quoteWithAnUnknownZoneIdIsRejected() throws Exception {
        quote("{\"weightKg\": 2, \"dropoffZoneId\": \"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("dropoffZoneId"));
    }

    @Test
    void bookingFillsInTheZonesFromTheCoordinates() throws Exception {
        String body = """
                {"pickupAddress": "12 Galle Road", "dropoffAddress": "45 Peradeniya Road", "weightKg": 2,
                 "pickupCity": "Colombo", "pickupLat": 6.9271, "pickupLng": 79.8612,
                 "dropoffCity": "Kandy", "dropoffLat": 7.2906, "dropoffLng": 80.6337,
                 "recipientName": "Kamal", "recipientPhone": "0771234567"}""";
        mvc.perform(post("/api/v1/parcels").with(as("CUSTOMER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pickupZoneId").value(COLOMBO))
                .andExpect(jsonPath("$.dropoffZoneId").value(KANDY))
                .andExpect(jsonPath("$.feeLkr").value(650.00));
    }

    // ---------- admin: zones ----------

    @Test
    void adminCreatesAZoneThatQuotesThenUseItsRule() throws Exception {
        JsonNode zone = createZone("Offshore A", box(79.05, 6.0), rule("FLAT_RATE", "900.00", "0.00", "1.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.currentRule.strategy").value("FLAT_RATE"))
                .andReturn().getResponse().getContentAsString().transform(this::read);

        assertThat(lookup.findZone(6.05, 79.1)).map(ZoneRef::name).contains("Offshore A");
        quote(at(6.05, 79.1)).andExpect(status().isOk()).andExpect(jsonPath("$.feeLkr").value(900.00));

        String id = zone.get("id").asText();
        mvc.perform(patch("/api/v1/admin/zones/" + id + "/status").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\": false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        quote(at(6.05, 79.1)).andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/zones").with(as("CUSTOMER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Offshore A')]").isEmpty())
                .andExpect(jsonPath("$[?(@.name == 'Kandy')].boundary.type").value("Polygon"));
    }

    @Test
    void zonesMayNotOverlapAnActiveZone() throws Exception {
        String overColombo = polygon(79.8, 6.8, 79.95, 6.8, 79.95, 6.95, 79.8, 6.95);
        createZone("Overlap Test", overColombo, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("boundary"))
                .andExpect(jsonPath("$.errors[0].message").value(org.hamcrest.Matchers.containsString("Colombo")));
    }

    @Test
    void selfCrossingOutlinesAreRejected() throws Exception {
        String bowTie = polygon(79.05, 7.5, 79.25, 7.7, 79.25, 7.5, 79.05, 7.7);
        createZone("Bow Tie", bowTie, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("boundary"));
    }

    @Test
    void outlinesOutsideSriLankaAreRejected() throws Exception {
        createZone("Maldives", polygon(73.0, 4.0, 73.5, 4.0, 73.5, 4.5, 73.0, 4.5), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("The zone must be inside Sri Lanka"));
    }

    @Test
    void zoneNamesAreUniqueIgnoringCase() throws Exception {
        createZone("kandy", box(79.05, 8.0), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void adminRenamesAndRedrawsAZone() throws Exception {
        String id = createZone("Offshore B", box(79.05, 6.4), null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::read).get("id").asText();

        mvc.perform(put("/api/v1/admin/zones/" + id).with(as("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Offshore B2\", \"boundary\": " + box(79.05, 6.45) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Offshore B2"))
                .andExpect(jsonPath("$.boundary.coordinates[0][0][1]").value(6.45));
        assertThat(lookup.findZone(6.55, 79.1)).map(ZoneRef::name).contains("Offshore B2");
    }

    // ---------- admin: pricing rules ----------

    @Test
    void aNewRuleReplacesTheCurrentOneAndDeactivatingItRestoresTheOld() throws Exception {
        String id = createZone("Offshore C", box(79.05, 6.8), rule("WEIGHT_TIERED", "500.00", "50.00", "1.10"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString().transform(this::read).get("id").asText();

        JsonNode newer = mvc.perform(post("/api/v1/admin/zones/" + id + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(rule("FLAT_RATE", "700.00", "0.00", "1.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(true))
                .andReturn().getResponse().getContentAsString().transform(this::read);
        quote(at(6.85, 79.1)).andExpect(jsonPath("$.feeLkr").value(700.00));

        String tomorrow = LocalDate.now(ZoneId.of("Asia/Colombo")).plusDays(1).toString();
        mvc.perform(post("/api/v1/admin/zones/" + id + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\": \"FLAT_RATE\", \"baseFee\": 1, \"perKgFee\": 0, \"sizeMultiplier\": 1,"
                                + " \"validFrom\": \"" + tomorrow + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.current").value(false));
        quote(at(6.85, 79.1)).andExpect(jsonPath("$.feeLkr").value(700.00)); // tomorrow's rule not yet in force

        mvc.perform(post("/api/v1/admin/zones/" + id + "/pricing-rules/" + newer.get("id").asText() + "/deactivate")
                        .with(as("ADMIN")))
                .andExpect(status().isNoContent());
        quote(at(6.85, 79.1)).andExpect(jsonPath("$.feeLkr").value(600.00)); // back to 500 + 2 x 50

        mvc.perform(get("/api/v1/admin/zones/" + id + "/pricing-rules").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void rulesCannotStartInThePast() throws Exception {
        String yesterday = LocalDate.now(ZoneId.of("Asia/Colombo")).minusDays(1).toString();
        mvc.perform(post("/api/v1/admin/zones/" + KANDY + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\": \"FLAT_RATE\", \"baseFee\": 1, \"perKgFee\": 0, \"sizeMultiplier\": 1,"
                                + " \"validFrom\": \"" + yesterday + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("validFrom"));
    }

    @Test
    void distanceBasedIsNotAChoice() throws Exception {
        mvc.perform(post("/api/v1/admin/zones/" + KANDY + "/pricing-rules").with(as("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(rule("DISTANCE_BASED", "1", "1", "1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("strategy"));
    }

    @Test
    void unknownZonesAndRulesAre404() throws Exception {
        mvc.perform(get("/api/v1/admin/zones/" + UUID.randomUUID()).with(as("ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/admin/zones/" + KANDY + "/pricing-rules/" + UUID.randomUUID() + "/deactivate")
                .with(as("ADMIN"))).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsManageZones() throws Exception {
        mvc.perform(get("/api/v1/admin/zones").with(as("DISPATCHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/zones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/zones").with(as("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Kandy')].currentRule.baseFee").value(450.0));
    }

    // ---------- helpers ----------

    private ResultActions quote(String body) throws Exception {
        return mvc.perform(post("/api/v1/quotes").with(as("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions createZone(String name, String boundary, String pricingRule) throws Exception {
        String body = "{\"name\": \"" + name + "\", \"boundary\": " + boundary
                + (pricingRule == null ? "" : ", \"pricingRule\": " + pricingRule) + "}";
        return mvc.perform(post("/api/v1/admin/zones").with(as("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String colomboToKandy(String weight, String strategy, String size) {
        return "{\"weightKg\": " + weight + ", \"pickupLat\": 6.9271, \"pickupLng\": 79.8612,"
                + " \"dropoffLat\": 7.2906, \"dropoffLng\": 80.6337"
                + (strategy == null ? "" : ", \"strategy\": \"" + strategy + "\"")
                + (size == null ? "" : ", \"size\": \"" + size + "\"") + "}";
    }

    private static String at(double lat, double lng) {
        return "{\"weightKg\": 2, \"dropoffLat\": " + lat + ", \"dropoffLng\": " + lng + "}";
    }

    private static String rule(String strategy, String baseFee, String perKgFee, String sizeMultiplier) {
        return "{\"strategy\": \"" + strategy + "\", \"baseFee\": " + baseFee + ", \"perKgFee\": " + perKgFee
                + ", \"sizeMultiplier\": " + sizeMultiplier + "}";
    }

    /** A 0.2 x 0.3 degree box with its south-west corner at (lng, lat), in the sea west of the island. */
    private static String box(double lng, double lat) {
        return polygon(lng, lat, lng + 0.2, lat, lng + 0.2, lat + 0.3, lng, lat + 0.3);
    }

    private static String polygon(double... lngLat) {
        StringBuilder ring = new StringBuilder();
        for (int i = 0; i < lngLat.length; i += 2) {
            ring.append('[').append(lngLat[i]).append(", ").append(lngLat[i + 1]).append("], ");
        }
        ring.append('[').append(lngLat[0]).append(", ").append(lngLat[1]).append(']');
        return "{\"type\": \"Polygon\", \"coordinates\": [[" + ring + "]]}";
    }

    private JsonNode read(String body) {
        try {
            return json.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static JwtRequestPostProcessor as(String role) {
        return jwt().jwt(j -> j.subject(UUID.randomUUID().toString()).claim("roles", java.util.List.of(role)))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
