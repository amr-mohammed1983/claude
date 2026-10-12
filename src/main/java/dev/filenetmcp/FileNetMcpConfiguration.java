package dev.filenetmcp;

import dev.filenetmcp.tools.Args;
import dev.filenetmcp.tools.ToolCatalog;
import dev.filenetmcp.tools.ToolDefCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class FileNetMcpConfiguration {
    /** Published to Spring AI, whose MCP auto-configuration registers every callback as an MCP tool. */
    @Bean
    ToolCallbackProvider fileNetTools(ToolCatalog catalog, FileNetProperties properties, JsonMapper json) {
        return ToolCallbackProvider.from(catalog.tools().stream()
                .map(t -> new ToolDefCallback(t, json, raw -> new Args(raw, properties))).toList());
    }
}
