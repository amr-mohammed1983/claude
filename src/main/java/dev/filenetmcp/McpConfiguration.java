package dev.filenetmcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.filenetmcp.mcp.McpServer;
import dev.filenetmcp.tools.Args;
import dev.filenetmcp.tools.ToolCatalog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class McpConfiguration {
    /** The MCP endpoint logic, shared by the stdio and HTTP transports. */
    @Bean
    McpServer mcpServer(ToolCatalog catalog, FileNetProperties properties, ObjectMapper json,
                        @Value("${mcp.server.name:filenet-mcp}") String name,
                        @Value("${mcp.server.version:0.1.0}") String version) {
        return new McpServer(name, version, catalog.tools(), json, raw -> new Args(raw, properties));
    }
}
