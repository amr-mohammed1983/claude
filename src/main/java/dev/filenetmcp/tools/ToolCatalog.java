package dev.filenetmcp.tools;

import dev.filenetmcp.FileNetProperties;
import dev.filenetmcp.gateway.FileNetGateway;
import dev.filenetmcp.gateway.FileNetGateway.Content;
import dev.filenetmcp.util.Util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** The complete list of tools. Each tool is a few lines: schema + one gateway call. */
@Component
public final class ToolCatalog {
    private static final String OS_DOC = "Object store symbolic name. Defaults to FILENET_OBJECT_STORE.";
    private static final String CLASS_DOC = "CE class symbolic name (e.g. Document, Folder, or a custom class). Default: ";
    private static final String REF_DOC = "Object ID ({GUID}) or repository path (e.g. /Invoices/2024/a.pdf).";
    private static final String PROPS_DOC = "Property symbolic name -> value. Dates: {\"$date\":\"2024-01-31T00:00:00Z\"}.";
    private static final String CONTENT_DOC = "Content: provide contentBase64 or filePath (local; confined to FILENET_FILE_ROOT).";

    private final FileNetGateway gw;
    private final FileNetProperties config;

    @Autowired
    public ToolCatalog(ObjectProvider<FileNetGateway> gateway, FileNetProperties config) {
        this(gateway.getIfAvailable(ToolCatalog::unavailableGateway), config);
    }

    public ToolCatalog(FileNetGateway gw, FileNetProperties config) {
        this.gw = gw;
        this.config = config;
    }

    /**
     * Stand-in used when Jace.jar is absent (no JaceGateway bean): the server still starts and lists tools,
     * and every call reports how to fix the setup.
     */
    private static FileNetGateway unavailableGateway() {
        LoggerFactory.getLogger(ToolCatalog.class).warn("JaceGateway not available: build with -Pjace and add Jace.jar to the classpath (docs/setup.md)");
        return (FileNetGateway) Proxy.newProxyInstance(ToolCatalog.class.getClassLoader(), new Class<?>[]{FileNetGateway.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("FileNet Content Engine API (Jace.jar) is not on the classpath; see docs/setup.md");
                });
    }

    /** The tools exposed under the current config (mutating tools are dropped in read-only mode). */
    public List<ToolDef> tools() {
        List<ToolDef> tools = new ArrayList<>();

        tools.add(os(ToolDef.builder("filenet_list_object_stores", "List the object stores in the FileNet domain.").readOnly(), false)
                .handle(a -> gw.listObjectStores()));

        tools.add(os(ToolDef.builder("filenet_get_object",
                "Fetch any FileNet object (document, folder, custom object) by ID or path.").readOnly(), true)
                .string("className", CLASS_DOC + "Document", false).string("ref", REF_DOC, true)
                .stringList("properties", "Property names to return; omit for all.")
                .handle(a -> gw.getObject(a.objectStore(), a.optStr("className", "Document"), a.str("ref"), a.optList("properties"))));

        tools.add(os(ToolDef.builder("filenet_search",
                "Run a Content Engine SQL query, e.g. SELECT This, DocumentTitle FROM Document WHERE DocumentTitle LIKE 'Inv%'.").readOnly(), true)
                .string("sql", "CE SQL statement (SELECT only).", true).integer("maxRows", "Max rows to return (default 50, max 1000).")
                .handle(a -> gw.search(a.objectStore(), a.str("sql"), Math.min(a.optInt("maxRows", 50), 1000))));

        tools.add(os(ToolDef.builder("filenet_describe_class", "List a class's property definitions (name, type, cardinality, required).").readOnly(), true)
                .string("className", "CE class symbolic name.", true)
                .handle(a -> gw.describeClass(a.objectStore(), a.str("className"))));

        tools.add(os(ToolDef.builder("filenet_list_folder", "List the sub-folders and documents of a folder.").readOnly(), true)
                .string("ref", REF_DOC, true)
                .handle(a -> gw.listFolder(a.objectStore(), a.str("ref"))));

        tools.add(os(ToolDef.builder("filenet_download_content",
                "Download a document's content element. Text types are returned inline, others as base64, unless saveTo is given.").readOnly(), true)
                .string("ref", REF_DOC, true).integer("elementIndex", "Zero-based content element (default 0).")
                .string("saveTo", "Local file path to write the content to instead of returning it.", false)
                .handle(a -> contentResult(a, gw.getContent(a.objectStore(), a.str("ref"), a.optInt("elementIndex", 0)))));

        tools.add(os(ToolDef.builder("filenet_create_document", "Create a document, optionally with content and filed into a folder."), true)
                .string("className", CLASS_DOC + "Document", false)
                .object("properties", PROPS_DOC + " e.g. {\"DocumentTitle\":\"Invoice 1\"}", false)
                .string("folderPath", "Folder path to file the document in, e.g. /Invoices.", false)
                .string("containmentName", "Name within the folder (defaults to the title / file name).", false)
                .string("contentBase64", CONTENT_DOC, false).string("filePath", CONTENT_DOC, false)
                .string("fileName", "Retrieval name for the content.", false).string("mimeType", "MIME type of the content.", false)
                .bool("checkin", "Check the document in as a major version (default true).")
                .handle(a -> gw.createDocument(a.objectStore(), a.optStr("className", "Document"), a.optMap("properties"),
                        a.optStr("folderPath", null), a.optStr("containmentName", null), a.optContent(), a.optBool("checkin", true))));

        tools.add(os(ToolDef.builder("filenet_create_folder", "Create a folder under a parent folder."), true)
                .string("className", CLASS_DOC + "Folder", false)
                .string("parentPath", "Parent folder path, e.g. / or /Invoices.", true).string("name", "New folder name.", true)
                .object("properties", PROPS_DOC, false)
                .handle(a -> gw.createFolder(a.objectStore(), a.optStr("className", "Folder"), a.str("parentPath"),
                        a.str("name"), a.optMap("properties"))));

        tools.add(os(ToolDef.builder("filenet_update_object", "Update properties of any object."), true)
                .string("className", CLASS_DOC + "Document", false).string("ref", REF_DOC, true)
                .object("properties", PROPS_DOC, true)
                .handle(a -> gw.updateObject(a.objectStore(), a.optStr("className", "Document"), a.str("ref"), a.optMap("properties"))));

        tools.add(os(ToolDef.builder("filenet_delete_object", "Permanently delete an object (all versions for a document).").destructive(), true)
                .string("className", CLASS_DOC + "Document", false).string("ref", REF_DOC, true)
                .handle(a -> {
                    gw.deleteObject(a.objectStore(), a.optStr("className", "Document"), a.str("ref"));
                    return Util.mapOf("deleted", a.str("ref"));
                }));

        tools.add(os(ToolDef.builder("filenet_checkout", "Reserve (check out) a document."), true)
                .string("ref", REF_DOC, true).handle(a -> gw.checkout(a.objectStore(), a.str("ref"))));

        tools.add(os(ToolDef.builder("filenet_cancel_checkout", "Cancel a document's checkout."), true)
                .string("ref", REF_DOC, true).handle(a -> gw.cancelCheckout(a.objectStore(), a.str("ref"))));

        tools.add(os(ToolDef.builder("filenet_checkin", "Check in a reserved document as a new version, optionally replacing content."), true)
                .string("ref", REF_DOC, true).object("properties", PROPS_DOC, false)
                .string("contentBase64", CONTENT_DOC, false).string("filePath", CONTENT_DOC, false)
                .string("fileName", "Retrieval name for the content.", false).string("mimeType", "MIME type of the content.", false)
                .bool("majorVersion", "Check in as a major version (default true).")
                .handle(a -> gw.checkin(a.objectStore(), a.str("ref"), a.optMap("properties"), a.optContent(),
                        a.optBool("majorVersion", true))));

        if (!config.readOnly()) return tools;
        List<ToolDef> readOnly = new ArrayList<>();
        for (ToolDef t : tools) if (t.readOnly()) readOnly.add(t);
        return readOnly;
    }

    /** Adds the shared objectStore argument. */
    private static ToolDef.Builder os(ToolDef.Builder b, boolean withObjectStore) {
        return withObjectStore ? b.string("objectStore", OS_DOC, false) : b;
    }

    /** Shapes downloaded content into a result: saved to disk, inline text, or base64. */
    private static Object contentResult(Args a, Content c) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fileName", c.fileName());
        out.put("mimeType", c.mimeType());
        out.put("bytes", c.data().length);
        String saveTo = a.optStr("saveTo", null);
        try {
            if (saveTo != null) {
                Files.write(a.safePath(saveTo), c.data());
                out.put("savedTo", saveTo);
            } else if (c.mimeType() != null && (c.mimeType().startsWith("text/") || c.mimeType().contains("json")
                    || c.mimeType().contains("xml"))) {
                out.put("text", new String(c.data(), StandardCharsets.UTF_8));
            } else {
                out.put("base64", Base64.getEncoder().encodeToString(c.data()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }
}
