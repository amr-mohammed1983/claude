# Configuration

Settings are Spring Boot properties under `filenet.*` (class `FileNetProperties`). Every property can be set
as an environment variable (relaxed binding: `filenet.object-store` ⇔ `FILENET_OBJECT_STORE`), a command-line
argument (`--filenet.read-only=true`) or in an external `application.yml`.

| Environment variable | Required | Default | Description |
|----------------------|----------|---------|-------------|
| `FILENET_URI` | yes | – | CE endpoint, e.g. `https://host:9443/wsi/FNCEWS40MTOM/` (WSI/HTTP) or `iiop://host:2809/FileNet/Engine` (EJB) |
| `FILENET_USERNAME` | yes | – | FileNet user |
| `FILENET_PASSWORD` | yes | – | Password |
| `FILENET_JAAS_STANZA` | no | `FileNetP8WSI` | JAAS stanza (`FileNetP8` for EJB) |
| `FILENET_OBJECT_STORE` | no | – | Default object store when a tool call omits `objectStore` |
| `FILENET_READ_ONLY` | no | `false` | `true` removes every tool that can change data |
| `FILENET_FILE_ROOT` | no | unset | If set, `filePath` / `saveTo` must be inside this directory |
| `FILENET_LOG_FILE` | no | `filenet-mcp.log` | Log file (stdio mode keeps stdout clean for the protocol) |

HTTP profile (`--spring.profiles.active=http`): `MCP_HTTP_PASSWORD` (required), `MCP_HTTP_USER` (`mcp`),
`MCP_HTTP_ADDRESS` (`127.0.0.1`), `MCP_HTTP_PORT` (`8080`).

## Security guidance

- The server acts with the **configured FileNet account's** permissions for every caller. Use a dedicated
  account with only the access the assistant needs; FileNet ACLs are the real authorization layer.
- Start with `FILENET_READ_ONLY=true`. `filenet_delete_object` is annotated destructive for clients that
  support it, but do not rely on client confirmation alone.
- `filePath`/`saveTo` touch the **machine running the server**; set `FILENET_FILE_ROOT` to a scratch directory.
- `filenet_search` takes raw CE SQL (query-only through `SearchSQL`); the account's read access still applies.
- HTTP mode: use a long random `MCP_HTTP_PASSWORD`, put TLS in front, and keep the bind address on localhost
  unless a reverse proxy fronts it. Don't commit secrets in client configs; prefer env/secret stores.
