package dev.filenetmcp;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {@code filenet.*}. Environment variables bind by relaxed naming
 * (FILENET_URI, FILENET_OBJECT_STORE, FILENET_READ_ONLY, ...); see docs/configuration.md.
 */
@ConfigurationProperties("filenet")
public record FileNetProperties(String uri, String username, String password,
                                @DefaultValue("FileNetP8WSI") String jaasStanza,
                                String objectStore,
                                @DefaultValue("false") boolean readOnly,
                                Path fileRoot) {}
