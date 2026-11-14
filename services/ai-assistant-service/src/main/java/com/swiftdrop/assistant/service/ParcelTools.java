package com.swiftdrop.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The assistant's three tools, used by the chat loop and the MCP endpoint alike. Each one calls our own API through
 * the gateway with the customer's JWT, so the normal permissions apply. Tool input comes from the model, so it is
 * validated here; results keep only whitelisted, non-personal fields and never include raw error bodies.
 */
@Component
public class ParcelTools {

    private static final Logger log = LoggerFactory.getLogger(ParcelTools.class);

    public static final String TRACK_PARCEL = "track_parcel";
    public static final String QUOTE_FEE = "quote_fee";
    public static final String LIST_MY_PARCELS = "list_my_parcels";

    private static final Pattern TRACKING_NO = Pattern.compile("[A-Za-z0-9-]{4,40}");
    private static final int MAX_LISTED = 10;

    /** City centres (lat, lng). parcel-service picks the zone pricing rule from these coordinates. */
    static final Map<String, double[]> CITIES = Map.ofEntries(
            Map.entry("Colombo", new double[]{6.9271, 79.8612}),
            Map.entry("Kandy", new double[]{7.2906, 80.6337}),
            Map.entry("Galle", new double[]{6.0535, 80.2210}),
            Map.entry("Jaffna", new double[]{9.6615, 80.0255}),
            Map.entry("Kurunegala", new double[]{7.4863, 80.3647}),
            Map.entry("Gampaha", new double[]{7.0917, 79.9999}),
            Map.entry("Negombo", new double[]{7.2083, 79.8358}),
            Map.entry("Matara", new double[]{5.9549, 80.5550}),
            Map.entry("Anuradhapura", new double[]{8.3114, 80.4037}),
            Map.entry("Trincomalee", new double[]{8.5874, 81.2152}),
            Map.entry("Batticaloa", new double[]{7.7310, 81.6747}),
            Map.entry("Ratnapura", new double[]{6.6828, 80.3992}),
            Map.entry("Badulla", new double[]{6.9934, 81.0550}),
            Map.entry("Nuwara Eliya", new double[]{6.9497, 80.7891}),
            Map.entry("Polonnaruwa", new double[]{7.9403, 81.0188}),
            Map.entry("Hambantota", new double[]{6.1241, 81.1185}),
            Map.entry("Kalutara", new double[]{6.5854, 79.9607}),
            Map.entry("Kegalle", new double[]{7.2513, 80.3464}),
            Map.entry("Matale", new double[]{7.4675, 80.6234}),
            Map.entry("Puttalam", new double[]{8.0362, 79.8283}),
            Map.entry("Vavuniya", new double[]{8.7514, 80.4971}),
            Map.entry("Ampara", new double[]{7.2975, 81.6820}),
            Map.entry("Moratuwa", new double[]{6.7730, 79.8816}),
            Map.entry("Chilaw", new double[]{7.5758, 79.7953}),
            Map.entry("Mannar", new double[]{8.9810, 79.9044}));

    /** Box sizes in cm for the quote's volumetric weight: SMALL matches parcel-service's own default box. */
    private static final Map<String, int[]> SIZES = Map.of(
            "SMALL", new int[]{20, 15, 10},
            "MEDIUM", new int[]{35, 25, 20},
            "LARGE", new int[]{50, 40, 35});

    public record Spec(String name, String description, ObjectNode inputSchema) {
    }

    /** {@code isError} marks a failure of the tool itself; a refusal ("not on your account") is a normal answer. */
    public record Result(String text, boolean isError) {
    }

    private final RestClient gateway;
    private final ObjectMapper json;
    private final List<Spec> specs;

    public ParcelTools(RestClient.Builder builder, @Value("${swiftdrop.gateway-url}") String gatewayUrl, ObjectMapper json) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1) // no h2c upgrade: it drops POST bodies on plain http
                        .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.gateway = builder.clone().baseUrl(gatewayUrl).requestFactory(factory).build();
        this.json = json;
        this.specs = List.of(
                new Spec(TRACK_PARCEL, "Look up the current status and timeline of one SwiftDrop parcel by its tracking "
                        + "number (for example SD-20261110-AB12CD). Use it whenever the customer asks where a parcel is.",
                        schema(Map.of("trackingNo", prop("string", "The parcel's tracking number")), List.of("trackingNo"))),
                new Spec(QUOTE_FEE, "Get the delivery fee in LKR for sending a parcel between two Sri Lankan cities. "
                        + "Supported cities: " + String.join(", ", CITIES.keySet().stream().sorted().toList()) + ".",
                        schema(Map.of(
                                "pickupCity", prop("string", "City the parcel is sent from"),
                                "dropoffCity", prop("string", "City the parcel goes to"),
                                "weightKg", prop("number", "Parcel weight in kilograms"),
                                "size", enumProp(List.of("SMALL", "MEDIUM", "LARGE"), "Box size; MEDIUM if the customer did not say")),
                                List.of("pickupCity", "dropoffCity", "weightKg"))),
                new Spec(LIST_MY_PARCELS, "List the customer's own parcels with their tracking numbers and current status.",
                        schema(Map.of(), List.of())));
    }

    public List<Spec> specs() {
        return specs;
    }

    public boolean exists(String name) {
        return specs.stream().anyMatch(s -> s.name().equals(name));
    }

    /** Runs one tool as the customer whose access token is {@code bearer}. Never throws. */
    public Result call(String name, JsonNode input, String bearer) {
        JsonNode args = input == null || input.isNull() ? json.createObjectNode() : input;
        return switch (name) {
            case TRACK_PARCEL -> trackParcel(args.path("trackingNo").asText(""), bearer);
            case QUOTE_FEE -> quoteFee(args, bearer);
            case LIST_MY_PARCELS -> listMyParcels(bearer);
            default -> new Result("Unknown tool.", true);
        };
    }

    private Result trackParcel(String trackingNo, String bearer) {
        String trimmed = trackingNo.trim();
        if (!TRACKING_NO.matcher(trimmed).matches()) {
            return new Result("That does not look like a SwiftDrop tracking number.", false);
        }
        return get(TRACK_PARCEL, "/api/v1/tracking/{trackingNo}", bearer, "I can't find that parcel on your account.",
                body -> {
                    ObjectNode out = json.createObjectNode();
                    out.put("trackingNo", trimmed);
                    copy(body, out, "status", "status", "currentStatus");
                    copy(body, out, "pickupCity", "pickupCity");
                    copy(body, out, "dropoffCity", "dropoffCity");
                    ArrayNode timeline = out.putArray("timeline");
                    JsonNode events = body.has("timeline") ? body.get("timeline") : body.path("history");
                    for (JsonNode e : events) {
                        ObjectNode event = timeline.addObject();
                        copy(e, event, "status", "status", "toStatus");
                        copy(e, event, "at", "at", "occurredAt", "timestamp");
                    }
                    return out;
                }, trimmed);
    }

    private Result quoteFee(JsonNode args, String bearer) {
        String pickup = city(args.path("pickupCity").asText(""));
        String dropoff = city(args.path("dropoffCity").asText(""));
        if (pickup == null || dropoff == null) {
            return new Result("I can only quote between these cities: "
                    + String.join(", ", CITIES.keySet().stream().sorted().toList()) + ".", false);
        }
        double weight = args.path("weightKg").asDouble(0);
        if (!(weight > 0 && weight <= 100)) {
            return new Result("The weight must be more than 0 and at most 100 kg.", false);
        }
        String size = args.path("size").asText("MEDIUM").toUpperCase(Locale.ROOT);
        if (!SIZES.containsKey(size)) {
            size = "MEDIUM";
        }
        int[] box = SIZES.get(size);

        ObjectNode request = json.createObjectNode()
                .put("weightKg", weight)
                .put("lengthCm", box[0]).put("widthCm", box[1]).put("heightCm", box[2])
                .put("size", size)
                .put("pickupLat", CITIES.get(pickup)[0]).put("pickupLng", CITIES.get(pickup)[1])
                .put("dropoffLat", CITIES.get(dropoff)[0]).put("dropoffLng", CITIES.get(dropoff)[1]);
        try {
            JsonNode body = gateway.post().uri("/api/v1/quotes")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .body(request).retrieve().body(JsonNode.class);
            ObjectNode out = json.createObjectNode().put("from", pickup).put("to", dropoff).put("weightKg", weight);
            copy(body, out, "feeLkr", "feeLkr");
            copy(body, out, "currency", "currency");
            copy(body, out, "chargeableWeightKg", "chargeableWeightKg");
            return new Result(out.toString(), false);
        } catch (RestClientException e) {
            return failure(QUOTE_FEE, e, "I can't get a quote for your account right now.");
        }
    }

    private Result listMyParcels(String bearer) {
        return get(LIST_MY_PARCELS, "/api/v1/parcels/mine", bearer, "I can't see any parcels on your account.",
                body -> {
                    ArrayNode out = json.createArrayNode();
                    for (JsonNode p : body) {
                        if (out.size() == MAX_LISTED) {
                            break;
                        }
                        ObjectNode parcel = out.addObject();
                        copy(p, parcel, "trackingNo", "trackingNo");
                        copy(p, parcel, "status", "currentStatus", "status");
                        copy(p, parcel, "pickupCity", "pickupCity");
                        copy(p, parcel, "dropoffCity", "dropoffCity");
                        copy(p, parcel, "createdAt", "createdAt");
                    }
                    return out;
                });
    }

    private Result get(String tool, String path, String bearer, String refusal, Function<JsonNode, JsonNode> keep, Object... uriVars) {
        try {
            JsonNode body = gateway.get().uri(path, uriVars)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
                    .retrieve().body(JsonNode.class);
            JsonNode kept = keep.apply(body == null ? json.createObjectNode() : body);
            if (kept.isArray() && kept.isEmpty()) {
                return new Result("You have no parcels yet.", false);
            }
            return new Result(kept.toString(), false);
        } catch (RestClientException e) {
            return failure(tool, e, refusal);
        }
    }

    private static Result failure(String tool, RestClientException e, String refusal) {
        if (e instanceof HttpStatusCodeException http) {
            HttpStatusCode status = http.getStatusCode();
            log.info("Tool {} got HTTP {}", tool, status.value());
            if (status.value() == 403 || status.value() == 404) {
                return new Result(refusal, false);
            }
            if (status.value() == 401) {
                return new Result("I couldn't confirm your login. Please sign in again.", false);
            }
        } else {
            log.warn("Tool {} failed: {}", tool, e.getClass().getSimpleName());
        }
        return new Result("That service isn't available right now. Please try again shortly.", true);
    }

    /** Copies the first present field of {@code names} from {@code from} into {@code to} as {@code target}. */
    private static void copy(JsonNode from, ObjectNode to, String target, String... names) {
        if (from == null) {
            return;
        }
        for (String name : names) {
            JsonNode value = from.get(name);
            if (value != null && !value.isNull() && value.isValueNode()) {
                to.set(target, value);
                return;
            }
        }
    }

    private static String city(String name) {
        String wanted = name.trim();
        return CITIES.keySet().stream().filter(c -> c.equalsIgnoreCase(wanted)).findFirst().orElse(null);
    }

    private ObjectNode schema(Map<String, ObjectNode> properties, List<String> required) {
        ObjectNode schema = json.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        properties.forEach(props::set);
        required.forEach(schema.putArray("required")::add);
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode prop(String type, String description) {
        return json.createObjectNode().put("type", type).put("description", description);
    }

    private ObjectNode enumProp(List<String> values, String description) {
        ObjectNode p = prop("string", description);
        values.forEach(p.putArray("enum")::add);
        return p;
    }
}
