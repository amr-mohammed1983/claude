package dev.filenetmcp.mcp;

import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Streamable HTTP transport (profile "http"): POST a JSON-RPC message, get a JSON reply, or 202 for
 * notifications. No SSE stream is offered, so GET returns 405 as the MCP spec allows.
 */
@RestController
@Profile("http")
class McpHttpController {
    private final McpServer server;

    McpHttpController(McpServer server) {
        this.server = server;
    }

    @PostMapping("${mcp.http.endpoint:/mcp}")
    ResponseEntity<String> post(@RequestBody String body) {
        String response = server.handle(body);
        return response == null ? ResponseEntity.accepted().<String>build()
                : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
    }
}
