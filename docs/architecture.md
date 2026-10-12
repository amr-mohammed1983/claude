# Architecture

```
dev.filenetmcp
├── Main                    wires Config → JaceGateway (by reflection) → ToolCatalog → McpBridge
├── Config                  environment variables
├── gateway/FileNetGateway  interface: what the tools need from FileNet (plain Java types)
├── jace/JaceGateway        the ONLY class that uses the FileNet Content Engine Java API
└── tools/
    ├── ToolDef             tool metadata + JSON schema builder + handler (SDK-independent)
    ├── Args                argument parsing/validation, content + path handling
    ├── ToolCatalog         the list of tools: schema + one gateway call each
    └── McpBridge           the ONLY class that uses the MCP Java SDK
```

## No-duplication principles

- **Class-agnostic operations.** Documents, folders and custom objects share `get/update/delete` tools and one
  fetch path (`JaceGateway.fetch`), parameterised by `className`, instead of per-type copies.
- **One adapter per external API.** MCP SDK code lives only in `McpBridge`; Jace code only in `JaceGateway`.
- **Shared helpers.** Argument parsing (`Args`), content attach (`attach`), property (de)serialisation
  (`toMap` / `apply`), session handling (`call`), and common schema fragments (`ToolCatalog` constants).
- **Why a gateway interface?** It is the seam that lets everything except `JaceGateway` build and be tested
  without the proprietary Jace.jar. `JaceGateway` is excluded from compilation unless built with `-Pjace`.

## Adding a tool

1. If FileNet access is new, add a method to `FileNetGateway` and implement it in `JaceGateway`
   (reuse `fetch`, `toMap`, `apply`, `call`).
2. Add one `tools.add(...)` block in `ToolCatalog.build` (mark `.readOnly()` if it cannot change data).
3. Document it in `docs/tools.md` (a unit test fails if a tool is missing from that file).

## Threading and sessions

Jace stores the authenticated JAAS `Subject` in a thread-local `UserContext`. `JaceGateway.call` pushes the
subject for the duration of every operation and pops it in `finally`, so any SDK worker thread is safe.
The connection, domain and object-store handles are cached.

## Testing

`mvn test` covers the catalogue (schemas, read-only filtering, defaults, content decoding, path confinement,
error mapping) using a recording proxy of `FileNetGateway`. The MCP stdio handshake, `tools/list` and an
error `tools/call` were also verified end-to-end by piping JSON-RPC into the built jar.

## Known limitations

- **`JaceGateway` is unverified against a live server.** It compiles against a hand-written stub of the Jace
  API signatures used (not IBM's real jar), so signature mismatches (overloads, checked types such as
  `checkout(...)` parameters, `PropertyFilter` construction) are possible. Run the smoke tests in
  `docs/setup.md` against a dev object store and fix any compile errors with the real jar first.
- Property type coercion is generic (`Properties.putObjectValue`): Integer/Boolean/Double/String/Date/lists.
  Object-valued (reference) and binary properties are not supported for writes.
- Collections (e.g. `ContentElements`) are not expanded by `filenet_get_object`; use the dedicated tools.
- Stdio transport only; one FileNet identity per server process.
- Not covered: security/ACL editing, annotations, retention, workflow (Process Engine), CMIS/GraphQL
  endpoints. Those can be added as new gateway methods + catalogue entries.
- The process does not exit on stdin EOF by itself; MCP clients terminate it.
