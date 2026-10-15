package dev.filenetmcp;

import static org.junit.jupiter.api.Assertions.*;

import dev.filenetmcp.mcp.McpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Boots the real application context (no Jace.jar, no stdio loop) and drives a tool through McpServer. */
@SpringBootTest(properties = {"mcp.stdio.enabled=false", "filenet.object-store=OS1", "filenet.read-only=false"})
@Import(FakeGatewayConfig.class)
class SpringWiringTest {
    @Autowired McpServer server;

    @Test
    void allToolsPublishedAndDefaultsBoundFromProperties() {
        assertTrue(server.handle("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}").contains("filenet_delete_object"));
        String out = server.handle("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/call\",\"params\":{\"name\":\"filenet_get_object\",\"arguments\":{\"ref\":\"/a\"}}}");
        assertTrue(out.contains("OS1") && out.contains("Document"), out);
    }
}
