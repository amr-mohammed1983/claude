package dev.filenetmcp.tools;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;

import java.util.List;

/** The single adapter between {@link ToolDef} and the MCP Java SDK. */
public final class McpBridge {
    private static final McpJsonMapper JSON = McpJsonDefaults.getMapper();

    private McpBridge() {}

    public static McpSyncServer serveStdio(List<ToolDef> tools, dev.filenetmcp.Config config) {
        return McpServer.sync(new StdioServerTransportProvider(JSON))
                .serverInfo("filenet-mcp", "0.1.0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(tools.stream().map(t -> specification(t, config)).toList())
                .build();
    }

    public static SyncToolSpecification specification(ToolDef def, dev.filenetmcp.Config config) {
        var schema = new McpSchema.JsonSchema("object", def.properties(), def.required(), false, null, null);
        var annotations = new McpSchema.ToolAnnotations(null, def.readOnly(), def.destructive(), null, false, null);
        return SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name(def.name()).description(def.description())
                        .inputSchema(schema).annotations(annotations).build())
                .callHandler((exchange, request) -> invoke(def, new Args(request.arguments(), config)))
                .build();
    }

    /** Runs the handler; any failure becomes an MCP error result rather than a protocol error. */
    public static CallToolResult invoke(ToolDef def, Args args) {
        try {
            return CallToolResult.builder().addTextContent(JSON.writeValueAsString(def.handler().apply(args))).build();
        } catch (Exception e) {
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            return CallToolResult.builder().addTextContent(msg).isError(true).build();
        }
    }
}
