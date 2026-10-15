package dev.filenetmcp.tools;

import dev.filenetmcp.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** One MCP tool: metadata + JSON schema + handler. Independent of any transport (see McpServer). */
public final class ToolDef {
    private final String name;
    private final String description;
    private final Map<String, Object> properties;
    private final List<String> required;
    private final boolean readOnly;
    private final boolean destructive;
    private final Function<Args, Object> handler;

    public ToolDef(String name, String description, Map<String, Object> properties, List<String> required,
                   boolean readOnly, boolean destructive, Function<Args, Object> handler) {
        this.name = name;
        this.description = description;
        this.properties = properties;
        this.required = required;
        this.readOnly = readOnly;
        this.destructive = destructive;
        this.handler = handler;
    }

    public String name() { return name; }
    public String description() { return description; }
    public Map<String, Object> properties() { return properties; }
    public List<String> required() { return required; }
    public boolean readOnly() { return readOnly; }
    public boolean destructive() { return destructive; }
    public Function<Args, Object> handler() { return handler; }

    public static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    public static final class Builder {
        private final String name, description;
        private final Map<String, Object> props = new LinkedHashMap<>();
        private final List<String> required = new ArrayList<>();
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
            props.put(key, Util.mapOf("type", "array", "items", Util.mapOf("type", "string"), "description", doc));
            return this;
        }

        private Builder prop(String key, String type, String doc, boolean req) {
            props.put(key, Util.mapOf("type", type, "description", doc));
            if (req) required.add(key);
            return this;
        }

        public Builder readOnly() { this.readOnly = true; return this; }
        public Builder destructive() { this.destructive = true; return this; }

        public ToolDef handle(Function<Args, Object> handler) {
            return new ToolDef(name, description, Collections.unmodifiableMap(new LinkedHashMap<>(props)),
                    Collections.unmodifiableList(new ArrayList<>(required)), readOnly, destructive, handler);
        }
    }
}
