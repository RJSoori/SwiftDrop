package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/** Each tool against a WireMock gateway: JWT forwarding, field whitelisting, refusals and input validation. */
class ParcelToolsTest {

    private static final WireMockServer gateway = new WireMockServer(wireMockConfig().dynamicPort());
    private static final ObjectMapper json = new ObjectMapper();
    private ParcelTools tools;

    @BeforeAll
    static void start() {
        gateway.start();
    }

    @AfterAll
    static void stop() {
        gateway.stop();
    }

    @BeforeEach
    void setUp() {
        gateway.resetAll();
        tools = new ParcelTools(RestClient.builder(), gateway.baseUrl(), json);
    }

    private ParcelTools.Result call(String tool, String input) throws Exception {
        return tools.call(tool, json.readTree(input), "customer-token");
    }

    @Test
    void trackParcelForwardsTheJwtAndKeepsOnlySafeFields() throws Exception {
        gateway.stubFor(get("/api/v1/tracking/SD-1001").withHeader("Authorization", equalTo("Bearer customer-token"))
                .willReturn(okJson("""
                        {"trackingNo":"SD-1001","parcelId":"7b2f410c-68e9-4c65-99ba-08a60b7678f7","currentStatus":"OUT_FOR_DELIVERY",
                         "pickupCity":"Colombo","dropoffCity":"Kandy","updatedAt":"2026-11-11T09:00:00Z",
                         "recipientName":"Nimal Perera","recipientPhone":"+94771234567","dropoffAddress":"12 Temple Rd",
                         "timeline":[{"status":"CREATED","occurredAt":"2026-11-10T08:00:00Z","note":"call 0771234567"},
                                     {"status":"OUT_FOR_DELIVERY","occurredAt":"2026-11-11T09:00:00Z"}]}""")));

        ParcelTools.Result result = call(ParcelTools.TRACK_PARCEL, "{\"trackingNo\":\" SD-1001 \"}");

        assertThat(result.isError()).isFalse();
        assertThat(json.readTree(result.text())).isEqualTo(json.readTree("""
                {"trackingNo":"SD-1001","status":"OUT_FOR_DELIVERY","pickupCity":"Colombo","dropoffCity":"Kandy",
                 "timeline":[{"status":"CREATED","at":"2026-11-10T08:00:00Z"},
                             {"status":"OUT_FOR_DELIVERY","at":"2026-11-11T09:00:00Z"}]}"""));
        assertThat(result.text()).doesNotContain("Nimal", "+9477", "Temple", "0771234567");
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404})
    void trackParcelRefusesWithoutLeakingTheErrorBody(int status) throws Exception {
        gateway.stubFor(get("/api/v1/tracking/SD-9999").willReturn(aResponse().withStatus(status)
                .withHeader("Content-Type", "application/json").withBody("{\"detail\":\"owned by customer 42\"}")));

        ParcelTools.Result result = call(ParcelTools.TRACK_PARCEL, "{\"trackingNo\":\"SD-9999\"}");

        assertThat(result.text()).isEqualTo("I can't find that parcel on your account.");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void trackParcelRejectsSomethingThatIsNotATrackingNumber() throws Exception {
        ParcelTools.Result result = call(ParcelTools.TRACK_PARCEL, "{\"trackingNo\":\"../../internal/v1/x\"}");

        assertThat(result.text()).contains("does not look like");
        gateway.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void quoteMapsCitiesAndSizeAndKeepsThePrice() throws Exception {
        gateway.stubFor(post("/api/v1/quotes").willReturn(okJson("""
                {"feeLkr":530.00,"actualWeightKg":2,"volumetricWeightKg":3.5,"chargeableWeightKg":3.5,
                 "strategy":"WEIGHT_TIERED","currency":"LKR"}""")));

        ParcelTools.Result result = call(ParcelTools.QUOTE_FEE,
                "{\"pickupCity\":\"colombo\",\"dropoffCity\":\"Kandy\",\"weightKg\":2,\"size\":\"large\"}");

        assertThat(json.readTree(result.text())).isEqualTo(json.readTree("""
                {"from":"Colombo","to":"Kandy","weightKg":2.0,"feeLkr":530.00,"currency":"LKR","chargeableWeightKg":3.5}"""));
        gateway.verify(postRequestedFor(urlEqualTo("/api/v1/quotes"))
                .withHeader("Authorization", equalTo("Bearer customer-token"))
                .withRequestBody(equalToJson("""
                        {"weightKg":2.0,"lengthCm":50,"widthCm":40,"heightCm":35,
                         "pickupLat":6.9271,"pickupLng":79.8612,"dropoffLat":7.2906,"dropoffLng":80.6337}""")));
    }

    @Test
    void quoteRefusesUnknownCitiesAndBadWeightsWithoutCallingTheApi() throws Exception {
        assertThat(call(ParcelTools.QUOTE_FEE, "{\"pickupCity\":\"Atlantis\",\"dropoffCity\":\"Kandy\",\"weightKg\":2}").text())
                .startsWith("I can only quote between these cities:").contains("Colombo", "Jaffna");
        assertThat(call(ParcelTools.QUOTE_FEE, "{\"pickupCity\":\"Galle\",\"dropoffCity\":\"Kandy\",\"weightKg\":0}").text())
                .contains("weight");
        assertThat(call(ParcelTools.QUOTE_FEE, "{\"pickupCity\":\"Galle\",\"dropoffCity\":\"Kandy\",\"weightKg\":500}").text())
                .contains("weight");
        gateway.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void listMyParcelsDropsPersonalData() throws Exception {
        gateway.stubFor(get("/api/v1/parcels/mine").withHeader("Authorization", equalTo("Bearer customer-token"))
                .willReturn(okJson("""
                        [{"id":"7b6f","trackingNo":"SD-1001","currentStatus":"IN_TRANSIT","pickupCity":"Colombo",
                          "dropoffCity":"Galle","recipientName":"Nimal Perera","dropoffAddress":"12 Temple Rd",
                          "customerId":"c-1","createdAt":"2026-11-10T08:00:00Z"}]""")));

        ParcelTools.Result result = call(ParcelTools.LIST_MY_PARCELS, "{}");

        assertThat(json.readTree(result.text())).isEqualTo(json.readTree("""
                [{"trackingNo":"SD-1001","status":"IN_TRANSIT","pickupCity":"Colombo","dropoffCity":"Galle",
                  "createdAt":"2026-11-10T08:00:00Z"}]"""));
    }

    @Test
    void listMyParcelsSaysSoWhenThereAreNone() throws Exception {
        gateway.stubFor(get("/api/v1/parcels/mine").willReturn(okJson("[]")));

        assertThat(call(ParcelTools.LIST_MY_PARCELS, "{}").text()).isEqualTo("You have no parcels yet.");
    }

    @Test
    void serviceFailureIsAToolErrorWithoutDetails() throws Exception {
        gateway.stubFor(get("/api/v1/parcels/mine").willReturn(aResponse().withStatus(500).withBody("stack trace here")));

        ParcelTools.Result result = call(ParcelTools.LIST_MY_PARCELS, "{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.text()).doesNotContain("stack");
    }

    @Test
    void expiredLoginAndUnknownToolAreHandled() throws Exception {
        gateway.stubFor(get("/api/v1/parcels/mine").willReturn(aResponse().withStatus(401)));

        assertThat(call(ParcelTools.LIST_MY_PARCELS, "{}").text()).contains("sign in again");
        assertThat(tools.call("delete_everything", null, "customer-token").isError()).isTrue();
        assertThat(tools.exists("delete_everything")).isFalse();
        gateway.verify(1, getRequestedFor(urlEqualTo("/api/v1/parcels/mine")));
    }
}
