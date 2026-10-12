# Setup

## Prerequisites

- JDK 17+ and Maven 3.9+
- FileNet P8 5.7 **Content Engine Java API** client jars. Take `Jace.jar` from a FileNet client install
  (typically `<FileNet>/CEClient/lib/Jace.jar`) or from your Content Platform Engine server. Use the
  version matching your server (5.7). IBM does not publish it to Maven Central.
- Network access to the CE web-services endpoint and a FileNet account (a dedicated, least-privilege one).
- A JAAS configuration for the Jace login. Jace looks up the stanza named by `FILENET_JAAS_STANZA`
  (default `FileNetP8WSI`) in the file given by `-Djava.security.auth.login.config`. For the WSI transport:

  ```
  FileNetP8WSI {
    com.filenet.api.util.WSILoginModule required;
  };
  ```
  Save as e.g. `jaas.conf`. (`FileNetP8` with `FileNetP8LoginModule` is used for the EJB transport.)

## Build

```bash
mvn -Pjace -Djace.jar=/path/to/Jace.jar package      # -> target/filenet-mcp-0.1.0.jar (shaded, without Jace)
mvn test                                             # no Jace.jar needed
```

Jace.jar is only a compile-time (`system` scope) dependency and is deliberately not bundled; add it to
the runtime classpath yourself (license + it must match your server version).

Jace.jar depends on libraries bundled in the FileNet client (e.g. `javax.security`, SOAP/WSI support).
If startup reports `NoClassDefFoundError`, add the other jars from the client `lib` directory
(`/path/to/CEClient/lib/*`) to the classpath.

## Run

```bash
export FILENET_URI=https://ce-host:9443/wsi/FNCEWS40MTOM/
export FILENET_USERNAME=... FILENET_PASSWORD=... FILENET_OBJECT_STORE=OS1
java -Djava.security.auth.login.config=/path/jaas.conf \
     -cp "target/filenet-mcp-0.1.0.jar:/path/to/CEClient/lib/*" dev.filenetmcp.Main
```

The server speaks MCP over stdin/stdout; logs go to stderr. Authentication happens lazily on the first
tool call, so a wrong password shows up as a tool error rather than a startup failure.

## MCP client configuration

Claude Desktop (`claude_desktop_config.json`) / Claude Code (`.mcp.json`):

```json
{
  "mcpServers": {
    "filenet": {
      "command": "java",
      "args": ["-Djava.security.auth.login.config=/path/jaas.conf",
               "-cp", "/path/filenet-mcp-0.1.0.jar:/path/to/CEClient/lib/*", "dev.filenetmcp.Main"],
      "env": {
        "FILENET_URI": "https://ce-host:9443/wsi/FNCEWS40MTOM/",
        "FILENET_USERNAME": "svc_mcp",
        "FILENET_PASSWORD": "…",
        "FILENET_OBJECT_STORE": "OS1",
        "FILENET_READ_ONLY": "true"
      }
    }
  }
}
```

On Windows use `;` as the classpath separator.

## TLS to the Content Engine

If the CE uses a private CA, import it into a truststore and pass
`-Djavax.net.ssl.trustStore=/path/truststore.jks -Djavax.net.ssl.trustStorePassword=...`.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `JaceGateway not available` | Not built with `-Pjace`, or Jace.jar not on the classpath |
| `NoClassDefFoundError: com/filenet/...` | Missing client jars; add `CEClient/lib/*` |
| `No LoginModules configured for FileNetP8WSI` | JAAS file/stanza missing; set `-Djava.security.auth.login.config` |
| `EngineRuntimeException E_NOT_AUTHENTICATED` | Wrong user/password or URI |
| `E_OBJECT_NOT_FOUND` | Wrong `className`, path, or object store |
| Client shows no tools | Check stderr; stdout must carry only MCP protocol |
