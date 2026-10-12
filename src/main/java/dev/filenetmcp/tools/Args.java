package dev.filenetmcp.tools;

import dev.filenetmcp.Config;
import dev.filenetmcp.gateway.FileNetGateway.Content;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Typed, validated access to a tool call's raw argument map. All argument parsing lives here. */
public final class Args {
    private final Map<String, Object> raw;
    private final Config config;

    public Args(Map<String, Object> raw, Config config) {
        this.raw = raw == null ? Map.of() : raw;
        this.config = config;
    }

    public String str(String key) {
        String v = optStr(key, null);
        if (v == null || v.isBlank()) throw new IllegalArgumentException("Missing required argument: " + key);
        return v;
    }

    public String optStr(String key, String def) {
        Object v = raw.get(key);
        return v == null ? def : v.toString();
    }

    public int optInt(String key, int def) {
        Object v = raw.get(key);
        return v == null ? def : ((Number) v).intValue();
    }

    public boolean optBool(String key, boolean def) {
        Object v = raw.get(key);
        return v == null ? def : (Boolean) v;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> optMap(String key) {
        Object v = raw.get(key);
        return v == null ? Map.of() : (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    public List<String> optList(String key) {
        Object v = raw.get(key);
        return v == null ? List.of() : (List<String>) v;
    }

    /** Object store: explicit argument, else FILENET_OBJECT_STORE. */
    public String objectStore() {
        String os = optStr("objectStore", config.defaultObjectStore());
        if (os == null || os.isBlank())
            throw new IllegalArgumentException("objectStore is required (or set FILENET_OBJECT_STORE)");
        return os;
    }

    /** Content from {@code contentBase64} or {@code filePath}; null if neither was supplied. */
    public Content optContent() {
        String mime = optStr("mimeType", "application/octet-stream");
        if (raw.get("contentBase64") != null) {
            return new Content(optStr("fileName", "content"), mime, Base64.getDecoder().decode(str("contentBase64")));
        }
        if (raw.get("filePath") != null) {
            Path p = safePath(str("filePath"));
            try {
                return new Content(optStr("fileName", p.getFileName().toString()), mime, Files.readAllBytes(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    /** Resolves a local path, confined to FILENET_FILE_ROOT when configured. */
    public Path safePath(String path) {
        Path p = Path.of(path).toAbsolutePath().normalize();
        Path root = config.fileRoot();
        if (root != null && !p.startsWith(root))
            throw new IllegalArgumentException("Path is outside FILENET_FILE_ROOT: " + path);
        return p;
    }
}
