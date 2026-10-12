package dev.filenetmcp;

import static org.junit.jupiter.api.Assertions.*;

import dev.filenetmcp.gateway.FileNetGateway;
import dev.filenetmcp.gateway.FileNetGateway.Content;
import dev.filenetmcp.tools.*;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCatalogTest {
    /** Records the last gateway call and returns a canned value. */
    static final class Recorder {
        String method; Object[] args; Object result = Map.of("ok", true);
        FileNetGateway gateway() {
            return (FileNetGateway) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FileNetGateway.class},
                    (p, m, a) -> { method = m.getName(); args = a; return m.getReturnType() == void.class ? null : result; });
        }
    }

    static Config config(Map<String, String> env) { return Config.from(env); }

    static ToolDef tool(List<ToolDef> tools, String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void toolNamesAreUniqueAndSchemasConsistent() {
        var tools = ToolCatalog.build(new Recorder().gateway(), config(Map.of()));
        assertEquals(tools.size(), tools.stream().map(ToolDef::name).collect(Collectors.toSet()).size());
        for (ToolDef t : tools) {
            assertTrue(t.properties().keySet().containsAll(t.required()), t.name());
            assertDoesNotThrow(() -> McpBridge.specification(t, config(Map.of())), t.name());
        }
    }

    @Test
    void readOnlyModeDropsMutatingTools() {
        var tools = ToolCatalog.build(new Recorder().gateway(), config(Map.of("FILENET_READ_ONLY", "true")));
        assertTrue(tools.stream().allMatch(ToolDef::readOnly));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("filenet_search")));
        assertTrue(tools.stream().noneMatch(t -> t.name().equals("filenet_delete_object")));
    }

    @Test
    void defaultObjectStoreAndClassNameAreApplied() {
        var r = new Recorder();
        var cfg = config(Map.of("FILENET_OBJECT_STORE", "OS1"));
        var tool = tool(ToolCatalog.build(r.gateway(), cfg), "filenet_get_object");
        McpBridge.invoke(tool, new Args(Map.of("ref", "/a"), cfg));
        assertEquals("getObject", r.method);
        assertEquals(List.of("OS1", "Document", "/a", List.of()), List.of(r.args));
    }

    @Test
    void missingObjectStoreIsReportedAsToolError() {
        var cfg = config(Map.of());
        var tool = tool(ToolCatalog.build(new Recorder().gateway(), cfg), "filenet_search");
        var res = McpBridge.invoke(tool, new Args(Map.of("sql", "SELECT 1"), cfg));
        assertTrue(res.isError());
    }

    @Test
    void searchCapsMaxRows() {
        var r = new Recorder();
        var cfg = config(Map.of("FILENET_OBJECT_STORE", "OS1"));
        McpBridge.invoke(tool(ToolCatalog.build(r.gateway(), cfg), "filenet_search"),
                new Args(Map.of("sql", "SELECT This FROM Document", "maxRows", 99999), cfg));
        assertEquals(1000, r.args[2]);
    }

    @Test
    void createDocumentDecodesBase64Content() {
        var r = new Recorder();
        var cfg = config(Map.of("FILENET_OBJECT_STORE", "OS1"));
        var args = Map.<String, Object>of("properties", Map.of("DocumentTitle", "t"), "fileName", "a.txt",
                "mimeType", "text/plain", "contentBase64", Base64.getEncoder().encodeToString("hi".getBytes()));
        McpBridge.invoke(tool(ToolCatalog.build(r.gateway(), cfg), "filenet_create_document"), new Args(args, cfg));
        Content c = (Content) r.args[5];
        assertEquals("a.txt", c.fileName());
        assertArrayEquals("hi".getBytes(), c.data());
        assertEquals(true, r.args[6]); // checkin defaults to true
    }

    @Test
    void downloadReturnsInlineTextOrBase64OrSavesFile(@TempDir Path dir) throws Exception {
        var r = new Recorder();
        var cfg = config(Map.of("FILENET_OBJECT_STORE", "OS1", "FILENET_FILE_ROOT", dir.toString()));
        var tool = tool(ToolCatalog.build(r.gateway(), cfg), "filenet_download_content");

        r.result = new Content("a.txt", "text/plain", "hello".getBytes());
        assertTrue(text(McpBridge.invoke(tool, new Args(Map.of("ref", "x"), cfg))).contains("\"text\""));

        r.result = new Content("a.bin", "application/pdf", new byte[]{1, 2});
        assertTrue(text(McpBridge.invoke(tool, new Args(Map.of("ref", "x"), cfg))).contains("\"base64\""));

        Path out = dir.resolve("o.bin");
        McpBridge.invoke(tool, new Args(Map.of("ref", "x", "saveTo", out.toString()), cfg));
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(out));
    }

    @Test
    void fileRootBlocksPathEscape(@TempDir Path dir) {
        var cfg = config(Map.of("FILENET_OBJECT_STORE", "OS1", "FILENET_FILE_ROOT", dir.toString()));
        var tool = tool(ToolCatalog.build(new Recorder().gateway(), cfg), "filenet_create_document");
        var res = McpBridge.invoke(tool, new Args(Map.of("filePath", "/etc/passwd"), cfg));
        assertTrue(res.isError());
        assertTrue(text(res).contains("outside FILENET_FILE_ROOT"));
    }

    @Test
    void everyToolIsDocumented() throws Exception {
        String docs = Files.readString(Path.of("docs/tools.md"));
        for (ToolDef t : ToolCatalog.build(new Recorder().gateway(), config(Map.of())))
            assertTrue(docs.contains("`" + t.name() + "`"), "undocumented: " + t.name());
    }

    private static String text(io.modelcontextprotocol.spec.McpSchema.CallToolResult r) {
        return ((io.modelcontextprotocol.spec.McpSchema.TextContent) r.content().get(0)).text();
    }
}
