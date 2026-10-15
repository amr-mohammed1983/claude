package dev.filenetmcp.mcp;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MCP over stdio: one JSON-RPC message per line on stdin, one per line on stdout. stdout carries only
 * protocol traffic (logging goes to stderr and the log file, see logback-spring.xml). Returns on EOF.
 */
@Component
@ConditionalOnProperty(name = "mcp.stdio.enabled", havingValue = "true", matchIfMissing = true)
class StdioTransport implements CommandLineRunner {
    private final McpServer server;

    StdioTransport(McpServer server) {
        this.server = server;
    }

    @Override
    public void run(String... args) throws Exception {
        PrintStream out = new PrintStream(System.out, false, "UTF-8");
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (String line; (line = in.readLine()) != null; ) {
            if (line.trim().isEmpty()) continue;
            String response = server.handle(line);
            if (response != null) {
                out.print(response);
                out.print('\n');
                out.flush();
            }
        }
    }
}
