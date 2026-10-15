package dev.filenetmcp;

import static org.junit.jupiter.api.Assertions.*;

import dev.filenetmcp.gateway.FileNetGateway;
import dev.filenetmcp.gateway.FileNetGateway.Content;
import dev.filenetmcp.tools.*;
import dev.filenetmcp.util.Util;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolCatalogTest {
    /** Records the last gateway call and returns a canned value. */
    static final class Recorder {
        String method; Object[] args; Object result = Util.mapOf("ok", true);
        FileNetGateway gateway() {
            return (FileNetGateway) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FileNetGateway.class},
                    (p, m, a) -> { method = m.getName(); args = a; return m.getReturnType() == void.class ? null : result; });
        }
    }

    /** Outcome of running a tool handler, as McpServer would report it. */
    static final class Outcome {
        final boolean isError; final String text;
        Outcome(boolean isError, String text) { this.isError = isError; this.text = text; }
    }

    static FileNetProperties config(Map<String, String> env) {
        String root = env.get("FILENET_FILE_ROOT");
        return new FileNetProperties("https://x/wsi", "u", "p", "FileNetP8WSI", env.get("FILENET_OBJECT_STORE"),
                "true".equals(env.get("FILENET_READ_ONLY")), root == null ? null : Paths.get(root));
    }

    static Map<String, String> env(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static List<ToolDef> catalog(FileNetGateway gw, FileNetProperties cfg) { return new ToolCatalog(gw, cfg).tools(); }

    static Outcome invoke(ToolDef t, Args a) {
        try {
            return new Outcome(false, String.valueOf(t.handler().apply(a)));
        } catch (RuntimeException e) {
            return new Outcome(true, e.getMessage());
        }
    }

    static ToolDef tool(List<ToolDef> tools, String name) {
        for (ToolDef t : tools) if (t.name().equals(name)) return t;
        throw new NoSuchElementException(name);
    }

    @Test
    void toolNamesAreUniqueAndSchemasConsistent() {
        List<ToolDef> tools = catalog(new Recorder().gateway(), config(env()));
        assertEquals(13, tools.size());
        assertEquals(tools.size(), tools.stream().map(ToolDef::name).collect(Collectors.toSet()).size());
        for (ToolDef t : tools) assertTrue(t.properties().keySet().containsAll(t.required()), t.name());
    }

    @Test
    void readOnlyModeDropsMutatingTools() {
        List<ToolDef> tools = catalog(new Recorder().gateway(), config(env("FILENET_READ_ONLY", "true")));
        assertTrue(tools.stream().allMatch(ToolDef::readOnly));
        assertTrue(tools.stream().anyMatch(t -> t.name().equals("filenet_search")));
        assertTrue(tools.stream().noneMatch(t -> t.name().equals("filenet_delete_object")));
    }

    @Test
    void defaultObjectStoreAndClassNameAreApplied() {
        Recorder r = new Recorder();
        FileNetProperties cfg = config(env("FILENET_OBJECT_STORE", "OS1"));
        ToolDef tool = tool(catalog(r.gateway(), cfg), "filenet_get_object");
        invoke(tool, new Args(Util.mapOf("ref", "/a"), cfg));
        assertEquals("getObject", r.method);
        assertEquals(Arrays.<Object>asList("OS1", "Document", "/a", Collections.emptyList()), Arrays.asList(r.args));
    }

    @Test
    void missingObjectStoreIsReportedAsToolError() {
        FileNetProperties cfg = config(env());
        ToolDef tool = tool(catalog(new Recorder().gateway(), cfg), "filenet_search");
        assertTrue(invoke(tool, new Args(Util.mapOf("sql", "SELECT 1"), cfg)).isError);
    }

    @Test
    void searchCapsMaxRows() {
        Recorder r = new Recorder();
        FileNetProperties cfg = config(env("FILENET_OBJECT_STORE", "OS1"));
        invoke(tool(catalog(r.gateway(), cfg), "filenet_search"),
                new Args(Util.mapOf("sql", "SELECT This FROM Document", "maxRows", 99999), cfg));
        assertEquals(1000, r.args[2]);
    }

    @Test
    void createDocumentDecodesBase64Content() {
        Recorder r = new Recorder();
        FileNetProperties cfg = config(env("FILENET_OBJECT_STORE", "OS1"));
        Map<String, Object> args = Util.mapOf("properties", Util.mapOf("DocumentTitle", "t"), "fileName", "a.txt",
                "mimeType", "text/plain", "contentBase64", Base64.getEncoder().encodeToString("hi".getBytes()));
        invoke(tool(catalog(r.gateway(), cfg), "filenet_create_document"), new Args(args, cfg));
        Content c = (Content) r.args[5];
        assertEquals("a.txt", c.fileName());
        assertArrayEquals("hi".getBytes(), c.data());
        assertEquals(true, r.args[6]); // checkin defaults to true
    }

    @Test
    void downloadReturnsInlineTextOrBase64OrSavesFile(@TempDir Path dir) throws Exception {
        Recorder r = new Recorder();
        FileNetProperties cfg = config(env("FILENET_OBJECT_STORE", "OS1", "FILENET_FILE_ROOT", dir.toString()));
        ToolDef tool = tool(catalog(r.gateway(), cfg), "filenet_download_content");

        r.result = new Content("a.txt", "text/plain", "hello".getBytes());
        assertTrue(invoke(tool, new Args(Util.mapOf("ref", "x"), cfg)).text.contains("text=hello"));

        r.result = new Content("a.bin", "application/pdf", new byte[]{1, 2});
        assertTrue(invoke(tool, new Args(Util.mapOf("ref", "x"), cfg)).text.contains("base64=AQI="));

        Path out = dir.resolve("o.bin");
        invoke(tool, new Args(Util.mapOf("ref", "x", "saveTo", out.toString()), cfg));
        assertArrayEquals(new byte[]{1, 2}, Files.readAllBytes(out));
    }

    @Test
    void fileRootBlocksPathEscape(@TempDir Path dir) {
        FileNetProperties cfg = config(env("FILENET_OBJECT_STORE", "OS1", "FILENET_FILE_ROOT", dir.toString()));
        ToolDef tool = tool(catalog(new Recorder().gateway(), cfg), "filenet_create_document");
        Outcome res = invoke(tool, new Args(Util.mapOf("filePath", "/etc/passwd"), cfg));
        assertTrue(res.isError);
        assertTrue(res.text.contains("outside FILENET_FILE_ROOT"));
    }

    @Test
    void everyToolIsDocumented() throws Exception {
        String docs = new String(Files.readAllBytes(Paths.get("docs/tools.md")), "UTF-8");
        for (ToolDef t : catalog(new Recorder().gateway(), config(env())))
            assertTrue(docs.contains("`" + t.name() + "`"), "undocumented: " + t.name());
    }
}
