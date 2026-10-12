# Architecture

```
dev.filenetmcp
├── FileNetMcpApplication        Spring Boot entry point
├── FileNetProperties            filenet.* configuration (record)
├── FileNetMcpConfiguration      publishes the tools as a Spring AI ToolCallbackProvider
├── HttpSecurityConfiguration    Basic auth for the "http" profile only
├── gateway/FileNetGateway       interface: what the tools need from FileNet (plain Java types)
├── jace/
│   ├── JaceGateway              the ONLY class that uses the FileNet Content Engine Java API
│   └── JaceConfiguration        registers JaceGateway when Jace is on the classpath
└── tools/
    ├── ToolDef                  tool metadata + JSON-schema builder + handler (framework-independent)
    ├── Args                     argument parsing/validation, content + path handling
    ├── ToolCatalog              @Component: the list of tools (schema + one gateway call each)
    └── ToolDefCallback          the ONLY adapter to Spring AI (ToolDef → ToolCallback)
```

Spring AI's MCP auto-configuration (`spring-ai-starter-mcp-server`) collects every `ToolCallbackProvider`
bean and serves it over stdio, or over Streamable HTTP in the `http` profile
(`spring-ai-starter-mcp-server-webmvc`). We write no protocol code.

## No-duplication principles

- **Class-agnostic operations.** Documents, folders and custom objects share `get/update/delete` tools and one
  fetch path (`JaceGateway.fetch`), parameterised by `className`.
- **Tools are data, not annotated methods.** One `ToolDef` per tool and one adapter, instead of an
  `@Tool` method plus schema boilerplate per tool; shared schema fragments live in `ToolCatalog`.
- **One adapter per external API.** Spring AI code only in `ToolDefCallback`/`FileNetMcpConfiguration`;
  Jace code only in `jace/`.
- **Shared helpers.** `Args` (parsing, content, path confinement), `attach`, `toMap`/`apply`, and `call`
  (session handling) in `JaceGateway`.
- **Why a gateway interface?** It lets everything except `JaceGateway` build and be tested without the
  proprietary Jace.jar; `JaceGateway` is excluded from compilation unless built with `-Pjace`, and
  `JaceConfiguration` is conditional on the Jace classes.

## Adding a tool

1. If FileNet access is new, add a method to `FileNetGateway` and implement it in `JaceGateway`.
2. Add one `tools.add(...)` block in `ToolCatalog.tools()` (mark `.readOnly()` if it cannot change data).
3. Document it in `docs/tools.md` (a unit test fails if a tool is missing from that file) and update the
   tool count in `SpringWiringTest`.

## Threading and sessions

Jace keeps the authenticated JAAS `Subject` in a thread-local `UserContext`. `JaceGateway.call` pushes the
subject for each operation and pops it in `finally`, so any request thread (stdio or HTTP) is safe. The
connection, domain and object-store handles are cached.

## Testing

- `ToolCatalogTest`: schemas, read-only filtering, defaults, content decoding, path confinement, errors,
  docs coverage (gateway replaced by a recording proxy).
- `SpringWiringTest`: boots the real Spring context with a fake gateway and calls tools through the Spring AI
  callbacks.
- Manually verified: stdio JSON-RPC session against the built jar (initialize, tools/list, tool errors, Jace
  gateway loaded via `-Dloader.path`) and the HTTP profile (401 without credentials, 200 with).

## Known limitations

- **`JaceGateway` is unverified against a live server.** It compiles only against a hand-written stub of the
  Jace signatures used (not IBM's jar), so signature mismatches (e.g. `checkout(...)` parameters,
  `PropertyFilter`/`FilterElement` construction) are possible. Compile with the real jar and smoke-test on a
  dev object store before relying on it.
- Spring AI/MCP versions: Spring Boot 4.1.x, Spring AI 2.0.x (MCP Java SDK 2.0). Spring AI 2.x is young;
  pin versions in `pom.xml` and re-test on upgrade.
- Property coercion is generic (`Properties.putObjectValue`): Integer/Boolean/Double/String/Date/lists.
  Object-reference and binary properties aren't writable. Collections (e.g. `ContentElements`) aren't
  expanded by `filenet_get_object`.
- The jar is large (~35 MB) because it includes the web stack for the optional HTTP profile.
- Tool annotations (read-only/destructive hints) are not emitted by the Spring AI adapter, so MCP clients
  can't show them; read-only enforcement is server-side (`FILENET_READ_ONLY`).
- One FileNet identity per server process. Not covered: ACL editing, retention, workflow (Process Engine),
  CMIS/GraphQL endpoints.
