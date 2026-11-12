package com.swiftdrop.assistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.swiftdrop.assistant.service.ParcelTools;
import com.swiftdrop.common.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * The assistant's tools as an MCP server (Streamable HTTP transport, JSON responses, no server-sent stream).
 * Behind the gateway at /api/v1/assistant/mcp, so callers need a CUSTOMER JWT, and every tool call runs as that
 * customer exactly like in the chat. Implements initialize, ping, tools/list and tools/call; notifications get 202.
 * <p>
 * A hand-rolled endpoint rather than the Spring AI MCP starter: tool calls must run on the request thread so the
 * caller's JWT is the one forwarded to our API.
 */
@RestController
@RequestMapping("/api/v1/assistant/mcp")
@PreAuthorize("hasRole('CUSTOMER')")
public class McpController {

    static final String DEFAULT_PROTOCOL = "2025-06-18";
    private static final Set<String> SUPPORTED_PROTOCOLS = Set.of("2025-03-26", "2025-06-18", "2025-11-25");

    private final ParcelTools tools;
    private final ObjectMapper json;

    public McpController(ParcelTools tools, ObjectMapper json) {
        this.tools = tools;
        this.json = json;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> handle(@RequestBody JsonNode message) {
        JsonNode id = message.get("id");
        if (id == null || id.isNull()) {
            return ResponseEntity.accepted().build(); // notification (e.g. notifications/initialized)
        }
        JsonNode params = message.path("params");
        return ResponseEntity.ok(switch (message.path("method").asText()) {
            case "initialize" -> result(id, initialize(params));
            case "ping" -> result(id, json.createObjectNode());
            case "tools/list" -> result(id, listTools());
            case "tools/call" -> callTool(id, params);
            default -> error(id, -32601, "Method not found");
        });
    }

    /** No server-initiated stream: the spec allows 405 here. */
    @GetMapping
    public ResponseEntity<Void> stream() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    private ObjectNode initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText();
        ObjectNode result = json.createObjectNode()
                .put("protocolVersion", SUPPORTED_PROTOCOLS.contains(requested) ? requested : DEFAULT_PROTOCOL);
        result.putObject("capabilities").putObject("tools");
        result.putObject("serverInfo").put("name", "swiftdrop-assistant").put("version", "1.0.0");
        return result;
    }

    private ObjectNode listTools() {
        ObjectNode result = json.createObjectNode();
        ArrayNode list = result.putArray("tools");
        for (ParcelTools.Spec spec : tools.specs()) {
            list.addObject().put("name", spec.name()).put("description", spec.description())
                    .set("inputSchema", spec.inputSchema());
        }
        return result;
    }

    private ObjectNode callTool(JsonNode id, JsonNode params) {
        String name = params.path("name").asText();
        if (!tools.exists(name)) {
            return error(id, -32602, "Unknown tool");
        }
        ParcelTools.Result outcome = tools.call(name, params.get("arguments"), CurrentUser.jwt().getTokenValue());
        ObjectNode result = json.createObjectNode().put("isError", outcome.isError());
        result.putArray("content").addObject().put("type", "text").put("text", outcome.text());
        return result(id, result);
    }

    private ObjectNode result(JsonNode id, JsonNode result) {
        ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = json.createObjectNode().put("jsonrpc", "2.0");
        response.set("id", id);
        response.putObject("error").put("code", code).put("message", message);
        return response;
    }
}
