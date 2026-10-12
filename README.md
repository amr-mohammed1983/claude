# FileNet 5.7 MCP Server

An [MCP](https://modelcontextprotocol.io) server (Java 17+, stdio transport) that lets AI clients such as
Claude Desktop / Claude Code work with **IBM FileNet Content Manager / P8 5.7** through the official
**Content Engine Java API (Jace)**: browse object stores, search with CE SQL, read and write documents
and folders, download/upload content, and run check-out/check-in.

```
MCP client ──stdio──▶ filenet-mcp ──Jace (WSI/EJB)──▶ FileNet Content Engine 5.7
```

## Quick start

```bash
# 1. Build (Jace.jar ships with FileNet; it is not on Maven Central)
mvn -Pjace -Djace.jar=/opt/IBM/FileNet/CEClient/lib/Jace.jar package

# 2. Run (or register in your MCP client, see docs/setup.md)
export FILENET_URI=https://ce-host:9443/wsi/FNCEWS40MTOM/
export FILENET_USERNAME=p8admin FILENET_PASSWORD=secret FILENET_OBJECT_STORE=OS1
java -cp target/filenet-mcp-0.1.0.jar:/opt/IBM/FileNet/CEClient/lib/Jace.jar dev.filenetmcp.Main
```

`mvn test` runs without Jace.jar (the unit tests use a fake gateway).

## Tools

`filenet_list_object_stores`, `filenet_get_object`, `filenet_search`, `filenet_describe_class`,
`filenet_list_folder`, `filenet_download_content`, `filenet_create_document`, `filenet_create_folder`,
`filenet_update_object`, `filenet_delete_object`, `filenet_checkout`, `filenet_cancel_checkout`,
`filenet_checkin`. Full reference: [docs/tools.md](docs/tools.md).

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/setup.md](docs/setup.md) | Prerequisites, build, running, MCP client configuration, troubleshooting |
| [docs/configuration.md](docs/configuration.md) | Environment variables and security settings |
| [docs/tools.md](docs/tools.md) | Every tool: arguments, behaviour, examples |
| [docs/architecture.md](docs/architecture.md) | Design, how to add a tool, testing, known limitations |

## Safety

Set `FILENET_READ_ONLY=true` to hide all write tools, use a least-privilege FileNet account, and set
`FILENET_FILE_ROOT` to confine local file reads/writes. See [docs/configuration.md](docs/configuration.md).

## Status

The MCP layer and tool catalogue are covered by automated tests. `JaceGateway` was written against the
5.7 Content Engine Java API but has **not been run against a live FileNet server** (Jace.jar is
proprietary and unavailable to the build environment); see "Known limitations" in
[docs/architecture.md](docs/architecture.md) before production use.
