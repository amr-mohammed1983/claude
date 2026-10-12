package dev.filenetmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** FileNet 5.7 MCP server. Spring AI's MCP starter exposes every {@code ToolCallback} bean as an MCP tool. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FileNetMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(FileNetMcpApplication.class, args);
    }
}
