# FileNet 5.5 MCP Server (Java 8)

An [MCP](https://modelcontextprotocol.io) server built on **Java 8 + Spring Boot 2.7** (with a small built-in MCP implementation, since Spring AI and the MCP Java SDK need Java 17) that lets AI
clients (Claude Desktop, Claude Code, …) work with **IBM FileNet Content Manager / P8 5.5** through the
official **Content Engine Java API (Jace)**: browse object stores, search with CE SQL, read and write
documents and folders, download/upload content, and run check-out/check-in.

```
MCP client ──stdio (default) or Streamable HTTP + Basic auth──▶ built-in MCP server
                                                                  └─ Jace ──▶ FileNet Content Engine 5.5
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

Verified: unit tests (including MCP protocol tests), a Spring context test, an end-to-end stdio JSON-RPC session
against the built jar, and the HTTP profile (401 without credentials, success with). Class files are Java 8
(major version 52). **Not verified:** `JaceGateway` against a real FileNet server or
IBM's real `Jace.jar` (proprietary; I compiled it only against a hand-written stub of the API signatures).
See "Known limitations" in [docs/architecture.md](docs/architecture.md) before production use.
