package dev.filenetmcp;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code filenet.*}. Environment variables bind by relaxed naming
 * (FILENET_URI, FILENET_OBJECT_STORE, FILENET_READ_ONLY, ...); see docs/configuration.md.
 */
@ConfigurationProperties("filenet")
public final class FileNetProperties {
    private final String uri;
    private final String username;
    private final String password;
    private final String jaasStanza;
    private final String objectStore;
    private final boolean readOnly;
    private final Path fileRoot;

    @ConstructorBinding
    public FileNetProperties(String uri, String username, String password,
                             @DefaultValue("FileNetP8WSI") String jaasStanza,
                             String objectStore,
                             @DefaultValue("false") boolean readOnly,
                             Path fileRoot) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.jaasStanza = jaasStanza;
        this.objectStore = objectStore;
        this.readOnly = readOnly;
        this.fileRoot = fileRoot;
    }

    public String uri() { return uri; }
    public String username() { return username; }
    public String password() { return password; }
    public String jaasStanza() { return jaasStanza; }
    public String objectStore() { return objectStore; }
    public boolean readOnly() { return readOnly; }
    public Path fileRoot() { return fileRoot; }
}
