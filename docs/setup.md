# Setup

## Prerequisites

- JDK 17+ and Maven 3.9+
- FileNet P8 5.7 **Content Engine Java API** jars. Take `Jace.jar` (typically `<FileNet>/CEClient/lib/Jace.jar`)
  from a FileNet client install matching your server. IBM does not publish it to Maven Central.
- Network access to the CE web-services endpoint and a (least-privilege) FileNet account.
- A JAAS configuration. Jace looks up the stanza `FILENET_JAAS_STANZA` (default `FileNetP8WSI`) in the file
  given by `-Djava.security.auth.login.config`:

  ```
  FileNetP8WSI {
    com.filenet.api.util.WSILoginModule required;
  };
  ```
  (For the EJB transport use stanza `FileNetP8` with `FileNetP8LoginModule`.)

## Build

```bash
mvn -Pjace -Djace.jar=/path/to/Jace.jar package      # -> target/filenet-mcp-0.1.0.jar (Spring Boot executable jar)
mvn test                                             # no Jace.jar needed
```

Jace.jar is a compile-time (`system` scope) dependency only and is **not** bundled. The jar uses Spring Boot's
`PropertiesLauncher`, so you supply Jace and its companion jars at runtime with `-Dloader.path=<dir or jar,…>`
(point it at `CEClient/lib`). If startup reports `NoClassDefFoundError: com/filenet/...` or a missing
`javax.*`/SOAP class, the companion jars from that directory are missing.

Without Jace.jar the server still starts and lists tools; every call returns
"FileNet Content Engine API (Jace.jar) is not on the classpath".

## Run: stdio (default, for Claude Desktop / Claude Code)

```bash
export FILENET_URI=https://ce-host:9443/wsi/FNCEWS40MTOM/
export FILENET_USERNAME=... FILENET_PASSWORD=... FILENET_OBJECT_STORE=OS1
java -Djava.security.auth.login.config=/path/jaas.conf -Dloader.path=/path/CEClient/lib \
     -jar target/filenet-mcp-0.1.0.jar
```

stdout carries only the MCP protocol; application logs go to `filenet-mcp.log` (override with `FILENET_LOG_FILE`).
Authentication to FileNet happens on the first tool call, so bad credentials show up as a tool error.

MCP client config (`claude_desktop_config.json` / `.mcp.json`):

```json
{
  "mcpServers": {
    "filenet": {
      "command": "java",
      "args": ["-Djava.security.auth.login.config=/path/jaas.conf", "-Dloader.path=/path/CEClient/lib",
               "-jar", "/path/filenet-mcp-0.1.0.jar"],
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

## Run: Streamable HTTP (shared server)

```bash
export MCP_HTTP_PASSWORD='long-random-secret'      # required; user defaults to "mcp"
java ... -jar target/filenet-mcp-0.1.0.jar --spring.profiles.active=http
```

Serves MCP at `http://127.0.0.1:8080/mcp`, requires HTTP Basic on every request (401 otherwise) and binds to
localhost. Change with `MCP_HTTP_ADDRESS`, `MCP_HTTP_PORT`, `MCP_HTTP_USER`. **Basic auth is not encrypted:**
terminate TLS in front (reverse proxy, or `server.ssl.*` properties) before exposing it beyond localhost.
All HTTP callers share the one FileNet identity configured in `FILENET_*`.

## TLS to the Content Engine

For a private CA, add `-Djavax.net.ssl.trustStore=/path/truststore.jks -Djavax.net.ssl.trustStorePassword=...`.

## Troubleshooting

| Symptom | Likely cause |
|---------|--------------|
| `Jace.jar is not on the classpath` in tool results | Not built/run with Jace: use `-Pjace` to build and `-Dloader.path` to run |
| `NoClassDefFoundError: com/filenet/...` | Missing client jars in `loader.path` |
| `No LoginModules configured for FileNetP8WSI` | JAAS file/stanza missing; set `-Djava.security.auth.login.config` |
| `E_NOT_AUTHENTICATED` | Wrong user/password or URI |
| `E_OBJECT_NOT_FOUND` | Wrong `className`, path or object store |
| Client shows no tools / protocol errors | Something wrote to stdout; check `filenet-mcp.log` |
| HTTP 401 | Missing/incorrect Basic credentials |
