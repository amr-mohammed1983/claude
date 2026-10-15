# Architecture

```
dev.filenetmcp
├── FileNetMcpApplication        Spring Boot entry point
├── FileNetProperties            filenet.* configuration (constructor-bound class)
├── McpConfiguration             builds the McpServer bean from ToolCatalog
├── mcp/
│   ├── McpServer                the MCP protocol (JSON-RPC 2.0: initialize, ping, tools/list, tools/call)
│   ├── StdioTransport           newline-delimited JSON-RPC on stdin/stdout (default)
│   └── McpHttpController        POST /mcp, Streamable HTTP without SSE ("http" profile)
├── HttpSecurityConfiguration    Basic auth for the "http" profile only
├── gateway/FileNetGateway       interface: what the tools need from FileNet (plain Java types)
├── util/Util                    Java 8 stand-ins for Map.of / isBlank / readAllBytes
├── jace/
│   ├── JaceGateway              the ONLY class that uses the FileNet Content Engine Java API
│   └── JaceConfiguration        registers JaceGateway when Jace is on the classpath
└── tools/
    ├── ToolDef                  tool metadata + JSON-schema builder + handler (framework-independent)
    ├── Args                     argument parsing/validation, content + path handling
    └── ToolCatalog              @Component: the list of tools (schema + one gateway call each)
```

Spring AI and the MCP Java SDK require Java 17, so this project implements the (small) tools-only part of the
MCP spec itself in `McpServer`. Both transports just pass JSON-RPC text to `McpServer.handle`. Supported
protocol versions: 2025-06-18, 2025-03-26, 2024-11-05. Not implemented: resources, prompts, sampling, SSE
streams, JSON-RPC batches, sessions (the server is stateless).

## No-duplication principles

- **Class-agnostic operations.** Documents, folders and custom objects share `get/update/delete` tools and one
  fetch path (`JaceGateway.fetch`), parameterised by `className`.
- **Tools are data, not annotated methods.** One `ToolDef` per tool, served by the single
  `McpServer`, instead of an `@Tool` method plus schema boilerplate per tool; shared schema fragments live in `ToolCatalog`.
- **One place per concern.** MCP protocol code only in `mcp/`; Jace code only in `jace/`.
- **Shared helpers.** `Args` (parsing, content, path confinement), `attach`, `toMap`/`apply`, and `call`
  (session handling) in `JaceGateway`.
- **Why a gateway interface?** It lets everything except `JaceGateway` build and be tested without the
  proprietary Jace.jar; `JaceGateway` is excluded from compilation unless built with `-Pjace`, and
  `JaceConfiguration` is conditional on the Jace classes.

## Adding a tool

1. If FileNet access is new, add a method to `FileNetGateway` and implement it in `JaceGateway`.
2. Add one `tools.add(...)` block in `ToolCatalog.tools()` (mark `.readOnly()` if it cannot change data).
3. Document it in `docs/tools.md` (a unit test fails if a tool is missing from that file) and update the
   tool count in `McpServerTest`/`ToolCatalogTest`.

## Threading and sessions

Jace keeps the authenticated JAAS `Subject` in a thread-local `UserContext`. `JaceGateway.call` pushes the
subject for each operation and pops it in `finally`, so any request thread (stdio or HTTP) is safe. The
connection, domain and object-store handles are cached.

## Testing

- `ToolCatalogTest`: schemas, read-only filtering, defaults, content decoding, path confinement, errors,
  docs coverage (gateway replaced by a recording proxy).
- `McpServerTest`: JSON-RPC protocol behaviour (initialize/version negotiation, tools/list schemas, in-band tool
  errors, protocol error codes, notifications).
- `SpringWiringTest`: boots the real Spring context with a fake gateway and calls a tool through `McpServer`.
- `HttpTransportTest`: the `http` profile via MockMvc (401 without/with wrong credentials, JSON-RPC with them,
  202 for notifications).
- Manually verified: stdio JSON-RPC session against the built jar (initialize, tools/list, tool errors; stdout
  carries only protocol traffic) and the HTTP profile via MockMvc. Not yet verified: loading the real Jace 5.5
  jars via `-Dloader.path` and `JaceGateway` against a live server.

## Known limitations

- **`JaceGateway` is unverified against a live server.** It was written against the 5.7 API and compiled only against a hand-written stub of the
  Jace signatures used (not IBM's jar), so signature mismatches (e.g. `checkout(...)` parameters,
  `PropertyFilter`/`FilterElement` construction) are possible. Compile with the real jar and smoke-test on a
  dev object store before relying on it.
- Spring Boot 2.7.18 is the last Java 8-compatible line and is **out of open-source support**. Keep HTTP mode
  on localhost/behind a TLS proxy, and prefer stdio.
- The MCP implementation is hand-written and tools-only; test against your MCP client after upgrades.
- Property coercion is generic (`Properties.putObjectValue`): Integer/Boolean/Double/String/Date/lists.
  Object-reference and binary properties aren't writable. Collections (e.g. `ContentElements`) aren't
  expanded by `filenet_get_object`.
- The jar includes the web stack for the optional HTTP profile.
- Tools carry `readOnlyHint`/`destructiveHint` annotations; read-only enforcement is still server-side
  (`FILENET_READ_ONLY`).
- One FileNet identity per server process. Not covered: ACL editing, retention, workflow (Process Engine),
  CMIS/GraphQL endpoints.
