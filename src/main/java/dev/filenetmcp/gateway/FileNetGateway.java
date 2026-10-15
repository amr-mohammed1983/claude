package dev.filenetmcp.gateway;

import java.util.List;
import java.util.Map;

/**
 * Everything the MCP tools need from FileNet, expressed in plain Java types.
 * The only production implementation is {@code dev.filenetmcp.jace.JaceGateway}
 * (Content Engine Java API); tests use an in-memory fake.
 *
 * <p>{@code objectStore} is the symbolic name; {@code ref} is a GUID ("{...}") or a repository path.
 * Returned maps are JSON-friendly (strings, numbers, booleans, lists, nested maps).
 */
public interface FileNetGateway {

    /** Binary payload to attach to / read from a document. */
    final class Content {
        private final String fileName;
        private final String mimeType;
        private final byte[] data;

        public Content(String fileName, String mimeType, byte[] data) {
            this.fileName = fileName;
            this.mimeType = mimeType;
            this.data = data;
        }

        public String fileName() { return fileName; }
        public String mimeType() { return mimeType; }
        public byte[] data() { return data; }
    }

    List<Map<String, Object>> listObjectStores();

    /** Fetch any CE object. {@code properties} empty = all properties. */
    Map<String, Object> getObject(String objectStore, String className, String ref, List<String> properties);

    /** Run a CE SQL query (e.g. {@code SELECT This, DocumentTitle FROM Document WHERE ...}). */
    List<Map<String, Object>> search(String objectStore, String sql, int maxRows);

    Map<String, Object> createDocument(String objectStore, String className, Map<String, Object> properties,
                                       String folderPath, String containmentName, Content content, boolean checkin);

    Map<String, Object> createFolder(String objectStore, String className, String parentPath,
                                     String name, Map<String, Object> properties);

    Map<String, Object> updateObject(String objectStore, String className, String ref, Map<String, Object> properties);

    void deleteObject(String objectStore, String className, String ref);

    Map<String, Object> listFolder(String objectStore, String folderRef);

    /** @param elementIndex zero-based content element */
    Content getContent(String objectStore, String documentRef, int elementIndex);

    Map<String, Object> checkout(String objectStore, String documentRef);

    Map<String, Object> cancelCheckout(String objectStore, String documentRef);

    Map<String, Object> checkin(String objectStore, String documentRef, Map<String, Object> properties,
                                Content content, boolean majorVersion);

    /** Property definitions of a class (symbolic name, data type, cardinality, required). */
    List<Map<String, Object>> describeClass(String objectStore, String className);
}
