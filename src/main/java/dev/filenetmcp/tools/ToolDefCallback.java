package dev.filenetmcp.tools;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The single adapter between {@link ToolDef} and Spring AI: a {@link ToolCallback}, which Spring AI's MCP
 * auto-configuration publishes as an MCP tool. Failures propagate as exceptions, which Spring AI turns
 * into MCP error results.
 */
public final class ToolDefCallback implements ToolCallback {
    private final ToolDef def;
    private final JsonMapper json;
    private final ToolDefinition definition;
    private final java.util.function.Function<Map<String, Object>, Args> argsFactory;

    public ToolDefCallback(ToolDef def, JsonMapper json, java.util.function.Function<Map<String, Object>, Args> argsFactory) {
        this.def = def;
        this.json = json;
        this.argsFactory = argsFactory;
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", def.properties());
        schema.put("required", def.required());
        schema.put("additionalProperties", false);
        this.definition = ToolDefinition.builder().name(def.name()).description(def.description())
                .inputSchema(json.writeValueAsString(schema)).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return definition;
    }

    @Override
    public String call(String toolInput) {
        Map<String, Object> raw = toolInput == null || toolInput.isBlank() ? Map.of()
                : json.readValue(toolInput, new TypeReference<Map<String, Object>>() {});
        return json.writeValueAsString(def.handler().apply(argsFactory.apply(raw)));
    }
}
