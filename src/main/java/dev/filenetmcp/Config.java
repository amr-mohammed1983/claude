package dev.filenetmcp;

import java.nio.file.Path;
import java.util.Map;

/** Environment-driven configuration; see docs/configuration.md. */
public record Config(String uri, String username, String password, String stanza,
                     String defaultObjectStore, boolean readOnly, Path fileRoot) {

    public static Config fromEnv() {
        return from(System.getenv());
    }

    public static Config from(Map<String, String> env) {
        String root = env.get("FILENET_FILE_ROOT");
        return new Config(
                env.get("FILENET_URI"),
                env.get("FILENET_USERNAME"),
                env.get("FILENET_PASSWORD"),
                env.getOrDefault("FILENET_JAAS_STANZA", "FileNetP8WSI"),
                env.get("FILENET_OBJECT_STORE"),
                "true".equalsIgnoreCase(env.getOrDefault("FILENET_READ_ONLY", "false")),
                root == null || root.isBlank() ? null : Path.of(root).toAbsolutePath().normalize());
    }
}
