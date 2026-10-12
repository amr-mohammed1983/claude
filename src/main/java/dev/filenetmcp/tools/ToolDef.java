package dev.filenetmcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One MCP tool: metadata + JSON schema + handler. Independent of the MCP SDK (see McpBridge). */
public record ToolDef(String name, String description, Map<String, Object> properties, List<String> required,
                      boolean readOnly, boolean destructive, Function<Args, Object> handler) {

    public static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    public static final class Builder {
        private final String name, description;
        private final Map<String, Object> props = new LinkedHashMap<>();
        private final List<String> required = new java.util.ArrayList<>();
        private boolean readOnly, destructive;

        private Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public Builder string(String key, String doc, boolean req) { return prop(key, "string", doc, req); }
        public Builder integer(String key, String doc) { return prop(key, "integer", doc, false); }
        public Builder bool(String key, String doc) { return prop(key, "boolean", doc, false); }
        public Builder object(String key, String doc, boolean req) { return prop(key, "object", doc, req); }

        public Builder stringList(String key, String doc) {
            props.put(key, Map.of("type", "array", "items", Map.of("type", "string"), "description", doc));
            return this;
        }

        private Builder prop(String key, String type, String doc, boolean req) {
            props.put(key, Map.of("type", type, "description", doc));
            if (req) required.add(key);
            return this;
        }

        public Builder readOnly() { this.readOnly = true; return this; }
        public Builder destructive() { this.destructive = true; return this; }

        public ToolDef handle(Function<Args, Object> handler) {
            return new ToolDef(name, description, Map.copyOf(props), List.copyOf(required), readOnly, destructive, handler);
        }
    }
}
