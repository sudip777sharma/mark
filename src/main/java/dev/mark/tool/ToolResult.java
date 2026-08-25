package dev.mark.tool;

import java.util.Map;

public record ToolResult(boolean successful, String observation, Map<String, Object> metadata) {
    public ToolResult { metadata = Map.copyOf(metadata); }
    public static ToolResult success(String observation) { return new ToolResult(true, observation, Map.of()); }
}
