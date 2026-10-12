package dev.filenetmcp;

import dev.filenetmcp.gateway.FileNetGateway;
import dev.filenetmcp.tools.McpBridge;
import dev.filenetmcp.tools.ToolCatalog;

/** Starts the MCP server on stdio. */
public final class Main {
    private static final String JACE_GATEWAY = "dev.filenetmcp.jace.JaceGateway";

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnv();
        FileNetGateway gateway;
        try {
            gateway = (FileNetGateway) Class.forName(JACE_GATEWAY).getConstructor(Config.class).newInstance(config);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            throw new IllegalStateException("JaceGateway not available: build with -Pjace and put Jace.jar "
                    + "on the classpath (see docs/setup.md)", e);
        }
        McpBridge.serveStdio(ToolCatalog.build(gateway, config), config);
        Thread.currentThread().join(); // stdio transport runs on its own threads
    }
}
