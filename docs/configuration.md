# Configuration

All settings are environment variables (read once at startup, class `dev.filenetmcp.Config`).

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `FILENET_URI` | yes | – | Content Engine endpoint, e.g. `https://host:9443/wsi/FNCEWS40MTOM/` (WSI/HTTP, recommended) or `iiop://host:2809/FileNet/Engine` (EJB) |
| `FILENET_USERNAME` | yes | – | FileNet user |
| `FILENET_PASSWORD` | yes | – | Password |
| `FILENET_JAAS_STANZA` | no | `FileNetP8WSI` | JAAS stanza (`FileNetP8` for EJB transport) |
| `FILENET_OBJECT_STORE` | no | – | Default object store used when a tool call omits `objectStore` |
| `FILENET_READ_ONLY` | no | `false` | `true` removes every tool that can change data |
| `FILENET_FILE_ROOT` | no | unset | If set, `filePath` / `saveTo` must be inside this directory |

## Security guidance

- The server acts with the **configured account's** permissions for every caller. Use a dedicated
  account with only the object-store access the assistant needs; FileNet ACLs are the real authorization layer.
- Start with `FILENET_READ_ONLY=true`. `filenet_delete_object` is annotated `destructiveHint`, so MCP
  clients can ask for confirmation, but do not rely on that alone.
- `filePath`/`saveTo` touch the **machine running the server**. Set `FILENET_FILE_ROOT` to a scratch directory.
- `filenet_search` accepts raw CE SQL. It is passed to `SearchSQL`, which only supports queries
  (no data modification), but the account's read access still applies.
- Keep secrets out of client config files that are committed; prefer your OS secret store or a wrapper script.
