package dev.mark.tool;

import java.util.Map;
import java.util.UUID;

public record ToolRequest(UUID taskId, String action, Map<String, Object> arguments) {
    public ToolRequest { arguments = Map.copyOf(arguments); }
}
