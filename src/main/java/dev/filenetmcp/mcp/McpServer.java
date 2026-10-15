package dev.filenetmcp.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filenetmcp.tools.Args;
import dev.filenetmcp.tools.ToolDef;
import dev.filenetmcp.util.Util;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal Model Context Protocol server (tools only), transport-agnostic: JSON-RPC 2.0 text in, text out.
 * Implemented directly because the MCP Java SDK / Spring AI need Java 17.
 * Supports initialize, ping, tools/list and tools/call; notifications are accepted and produce no reply.
 */
public final class McpServer {
    private static final List<String> SUPPORTED_VERSIONS = Arrays.asList("2025-06-18", "2025-03-26", "2024-11-05");
    private static final int PARSE_ERROR = -32700, INVALID_REQUEST = -32600, METHOD_NOT_FOUND = -32601,
            INVALID_PARAMS = -32602, INTERNAL_ERROR = -32603;

    private final String name;
    private final String version;
    private final ObjectMapper json;
    private final Map<String, ToolDef> tools = new LinkedHashMap<>();
    private final Function<Map<String, Object>, Args> argsFactory;

    public McpServer(String name, String version, List<ToolDef> tools, ObjectMapper json,
                     Function<Map<String, Object>, Args> argsFactory) {
        this.name = name;
        this.version = version;
        this.json = json;
        this.argsFactory = argsFactory;
        for (ToolDef t : tools) this.tools.put(t.name(), t);
    }

    /** Handles one JSON-RPC message; returns the response JSON, or null when none is due (notifications). */
    public String handle(String message) {
        JsonNode req;
        try {
            req = json.readTree(message);
        } catch (IOException e) {
            return error(null, PARSE_ERROR, "Parse error");
        }
        if (req == null || !req.isObject()) return error(null, INVALID_REQUEST, "Invalid Request");
        JsonNode id = req.get("id");
        JsonNode method = req.get("method");
        if (method == null || !method.isTextual()) {
            // A response to something we never sent, or garbage.
            return req.has("result") || req.has("error") ? null : error(id, INVALID_REQUEST, "Invalid Request");
        }
        if (id == null) return null; // notification (notifications/initialized, notifications/cancelled, ...)
        try {
            return result(id, dispatch(method.asText(), req.path("params")));
        } catch (RpcException e) {
            return error(id, e.code, e.getMessage());
        } catch (RuntimeException e) {
            return error(id, INTERNAL_ERROR, String.valueOf(e.getMessage()));
        }
    }

    private Object dispatch(String method, JsonNode params) {
        switch (method) {
            case "initialize":
                return initialize(params);
            case "ping":
                return Collections.emptyMap();
            case "tools/list":
                return Util.mapOf("tools", toolList());
            case "tools/call":
                return callTool(params);
            default:
                throw new RpcException(METHOD_NOT_FOUND, "Method not found: " + method);
        }
    }

    private Object initialize(JsonNode params) {
        String requested = params.path("protocolVersion").asText("");
        return Util.mapOf(
                "protocolVersion", SUPPORTED_VERSIONS.contains(requested) ? requested : SUPPORTED_VERSIONS.get(0),
                "capabilities", Util.mapOf("tools", Util.mapOf("listChanged", false)),
                "serverInfo", Util.mapOf("name", name, "version", version));
    }

    private List<Object> toolList() {
        List<Object> out = new ArrayList<>();
        for (ToolDef t : tools.values()) {
            Map<String, Object> schema = Util.mapOf("type", "object", "properties", t.properties(),
                    "required", t.required(), "additionalProperties", false);
            out.add(Util.mapOf("name", t.name(), "description", t.description(), "inputSchema", schema,
                    "annotations", Util.mapOf("readOnlyHint", t.readOnly(), "destructiveHint", t.destructive())));
        }
        return out;
    }

    /** Tool failures are reported in-band (isError) so the model can see and react to them. */
    private Object callTool(JsonNode params) {
        ToolDef tool = tools.get(params.path("name").asText(""));
        if (tool == null) throw new RpcException(INVALID_PARAMS, "Unknown tool: " + params.path("name").asText(""));
        Map<String, Object> arguments = params.hasNonNull("arguments")
                ? json.convertValue(params.get("arguments"), new TypeReference<Map<String, Object>>() {})
                : Collections.<String, Object>emptyMap();
        String text;
        boolean isError = false;
        try {
            text = json.writeValueAsString(tool.handler().apply(argsFactory.apply(arguments)));
        } catch (Exception e) {
            isError = true;
            text = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
        }
        return Util.mapOf("content", Collections.singletonList(Util.mapOf("type", "text", "text", text)),
                "isError", isError);
    }

    private String result(JsonNode id, Object result) {
        return write(Util.mapOf("jsonrpc", "2.0", "id", id, "result", result));
    }

    private String error(JsonNode id, int code, String message) {
        return write(Util.mapOf("jsonrpc", "2.0", "id", id == null ? json.nullNode() : id,
                "error", Util.mapOf("code", code, "message", message)));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static final class RpcException extends RuntimeException {
        final int code;

        RpcException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
