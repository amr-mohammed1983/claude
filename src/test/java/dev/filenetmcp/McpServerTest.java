package dev.filenetmcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filenetmcp.mcp.McpServer;
import dev.filenetmcp.tools.Args;
import dev.filenetmcp.tools.ToolCatalog;
import dev.filenetmcp.util.Util;
import java.lang.reflect.Proxy;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import dev.filenetmcp.gateway.FileNetGateway;

/** Protocol-level tests of the hand-written MCP server (JSON-RPC in, JSON-RPC out). */
class McpServerTest {
    static final ObjectMapper JSON = new ObjectMapper();

    static McpServer server() {
        FileNetProperties cfg = ToolCatalogTest.config(ToolCatalogTest.env("FILENET_OBJECT_STORE", "OS1"));
        FileNetGateway gw = (FileNetGateway) Proxy.newProxyInstance(McpServerTest.class.getClassLoader(),
                new Class<?>[]{FileNetGateway.class}, (p, m, a) -> {
                    if (m.getName().equals("getObject")) return Util.mapOf("os", a[0], "className", a[1], "ref", a[2]);
                    throw new IllegalStateException("boom");
                });
        return new McpServer("filenet-mcp", "0.1.0", new ToolCatalog(gw, cfg).tools(), JSON, raw -> new Args(raw, cfg));
    }

    static JsonNode rpc(McpServer s, String message) throws Exception {
        String out = s.handle(message);
        assertNotNull(out, "expected a response");
        return JSON.readTree(out);
    }

    @Test
    void initializeEchoesSupportedProtocolVersionAndAdvertisesTools() throws Exception {
        JsonNode r = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2024-11-05\"}}");
        assertEquals(1, r.get("id").asInt());
        assertEquals("2024-11-05", r.at("/result/protocolVersion").asText());
        assertEquals("filenet-mcp", r.at("/result/serverInfo/name").asText());
        assertTrue(r.at("/result/capabilities").has("tools"));
        assertEquals("2025-06-18", rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"1999-01-01\"}}")
                .at("/result/protocolVersion").asText());
    }

    @Test
    void notificationsGetNoResponse() {
        assertNull(server().handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    void toolsListPublishesSchemas() throws Exception {
        JsonNode tools = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":\"a\",\"method\":\"tools/list\"}").at("/result/tools");
        assertEquals(13, tools.size());
        JsonNode search = null;
        for (JsonNode t : tools) if (t.get("name").asText().equals("filenet_search")) search = t;
        assertNotNull(search);
        assertEquals("object", search.at("/inputSchema/type").asText());
        assertTrue(search.at("/inputSchema/properties").has("sql"));
        assertEquals("sql", search.at("/inputSchema/required/0").asText());
        assertTrue(search.at("/annotations/readOnlyHint").asBoolean());
    }

    @Test
    void toolCallReturnsJsonTextAndUsesDefaults() throws Exception {
        JsonNode r = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"filenet_get_object\",\"arguments\":{\"ref\":\"/a\"}}}");
        assertFalse(r.at("/result/isError").asBoolean());
        JsonNode body = JSON.readTree(r.at("/result/content/0/text").asText());
        assertEquals("OS1", body.get("os").asText());
        assertEquals("Document", body.get("className").asText());
    }

    @Test
    void toolFailureIsReportedInBandNotAsProtocolError() throws Exception {
        JsonNode r = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"filenet_checkout\",\"arguments\":{\"ref\":\"x\"}}}");
        assertTrue(r.at("/result/isError").asBoolean());
        assertEquals("boom", r.at("/result/content/0/text").asText());
        JsonNode missing = rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":{\"name\":\"filenet_get_object\"}}");
        assertTrue(missing.at("/result/isError").asBoolean());
        assertTrue(missing.at("/result/content/0/text").asText().contains("Missing required argument: ref"));
    }

    @Test
    void protocolErrors() throws Exception {
        assertEquals(-32601, rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"nope\"}").at("/error/code").asInt());
        assertEquals(-32602, rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"nope\"}}").at("/error/code").asInt());
        JsonNode parse = rpc(server(), "{not json");
        assertEquals(-32700, parse.at("/error/code").asInt());
        assertTrue(parse.get("id").isNull());
        assertTrue(rpc(server(), "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}").at("/result").isObject());
    }
}
