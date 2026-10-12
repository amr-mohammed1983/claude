package dev.filenetmcp;

import static org.junit.jupiter.api.Assertions.*;

import dev.filenetmcp.gateway.FileNetGateway;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;

/** Boots the real application context (no Jace.jar) and drives tools through the Spring AI callbacks. */
@SpringBootTest(properties = {"spring.ai.mcp.server.stdio=false", "filenet.object-store=OS1", "filenet.read-only=false"})
class SpringWiringTest {
    @TestConfiguration
    static class Fake {
        @Bean
        FileNetGateway gateway() {
            return (FileNetGateway) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{FileNetGateway.class}, (p, m, a) -> switch (m.getName()) {
                        case "listObjectStores" -> List.of(Map.of("symbolicName", "OS1"));
                        case "getObject" -> Map.of("os", a[0], "className", a[1], "ref", a[2]);
                        default -> throw new IllegalStateException("boom");
                    });
        }
    }

    @Autowired ToolCallbackProvider provider;

    private ToolCallback tool(String name) {
        for (ToolCallback t : provider.getToolCallbacks()) if (t.getToolDefinition().name().equals(name)) return t;
        return fail("no tool " + name);
    }

    @Test
    void allToolsPublishedWithJsonSchema() {
        assertEquals(13, provider.getToolCallbacks().length);
        assertTrue(tool("filenet_search").getToolDefinition().inputSchema().contains("\"sql\""));
    }

    @Test
    void callUsesDefaultsFromProperties() {
        String out = tool("filenet_get_object").call("{\"ref\":\"/a\"}");
        assertTrue(out.contains("\"os\":\"OS1\"") && out.contains("\"className\":\"Document\""), out);
    }

    @Test
    void failuresPropagateAsExceptions() {
        assertThrows(RuntimeException.class, () -> tool("filenet_checkout").call("{\"ref\":\"x\"}"));
    }
}
