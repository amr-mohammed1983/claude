package dev.filenetmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** FileNet 5.5 MCP server (Java 8, Spring Boot 2.7). Tools are served by {@code dev.filenetmcp.mcp.McpServer}. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FileNetMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileNetMcpApplication.class, args);
    }
}
