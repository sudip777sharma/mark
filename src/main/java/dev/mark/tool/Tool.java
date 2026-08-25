package dev.mark.tool;

import java.util.Map;

public interface Tool {
    String name();
    default String description() { return "MARK tool: " + name(); }
    default Map<String, Object> parameterSchema() { return Map.of("type", "object", "properties", Map.of()); }
    ToolResult execute(ToolRequest request);
}
