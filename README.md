# FileNet 5.7 MCP Server (Spring AI 2.x)

An [MCP](https://modelcontextprotocol.io) server built on **Spring Boot 4 + Spring AI 2.x** that lets AI
clients (Claude Desktop, Claude Code, …) work with **IBM FileNet Content Manager / P8 5.7** through the
official **Content Engine Java API (Jace)**: browse object stores, search with CE SQL, read and write
documents and folders, download/upload content, and run check-out/check-in.

```
MCP client ──stdio (default) or Streamable HTTP + Basic auth──▶ Spring AI MCP server
                                                                  └─ Jace ──▶ FileNet Content Engine 5.7
```

## Quick start (stdio)

```bash
# 1. Build (Jace.jar ships with FileNet; it is not on Maven Central)
mvn -Pjace -Djace.jar=/opt/IBM/FileNet/CEClient/lib/Jace.jar package

# 2. Run; -Dloader.path adds the FileNet client jars to the classpath at runtime
export FILENET_URI=https://ce-host:9443/wsi/FNCEWS40MTOM/
export FILENET_USERNAME=p8admin FILENET_PASSWORD=secret FILENET_OBJECT_STORE=OS1
java -Djava.security.auth.login.config=jaas.conf -Dloader.path=/opt/IBM/FileNet/CEClient/lib \
     -jar target/filenet-mcp-0.1.0.jar
```

`mvn test` runs without Jace.jar. Streamable HTTP, MCP-client configuration and troubleshooting are in
[docs/setup.md](docs/setup.md).

## Tools

`filenet_list_object_stores`, `filenet_get_object`, `filenet_search`, `filenet_describe_class`,
`filenet_list_folder`, `filenet_download_content`, `filenet_create_document`, `filenet_create_folder`,
`filenet_update_object`, `filenet_delete_object`, `filenet_checkout`, `filenet_cancel_checkout`,
`filenet_checkin`. Full reference: [docs/tools.md](docs/tools.md).

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/setup.md](docs/setup.md) | Prerequisites, build, stdio and HTTP run modes, MCP client configuration, troubleshooting |
| [docs/configuration.md](docs/configuration.md) | `filenet.*` properties / environment variables and security settings |
| [docs/tools.md](docs/tools.md) | Every tool: arguments, behaviour, examples |
| [docs/architecture.md](docs/architecture.md) | Design, how to add a tool, testing, known limitations |

## Safety

Set `FILENET_READ_ONLY=true` to hide all write tools, use a least-privilege FileNet account, and set
`FILENET_FILE_ROOT` to confine local file reads/writes. HTTP mode requires Basic auth and binds to
localhost by default. See [docs/configuration.md](docs/configuration.md).

## Status

Verified: unit tests, a Spring context test, an end-to-end stdio JSON-RPC session, and the HTTP profile
(401 without credentials, success with). **Not verified:** `JaceGateway` against a real FileNet server or
IBM's real `Jace.jar` (proprietary; I compiled it only against a hand-written stub of the API signatures).
See "Known limitations" in [docs/architecture.md](docs/architecture.md) before production use.
