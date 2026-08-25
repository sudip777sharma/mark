package dev.mark.tool;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EchoTool implements Tool {
    public String name() { return "echo"; }
    public String description() { return "Echo a message exactly as provided."; }
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string", "description", "Message to echo")), "required", java.util.List.of("message"));
    }
    public ToolResult execute(ToolRequest request) {
        Object messageArgument = request.arguments().get("message");
        if (!(messageArgument instanceof String message) || message.isBlank()) {
            return new ToolResult(false, "echo requires a non-blank string message argument", Map.of());
        }
        return ToolResult.success("Echoed: " + message);
    }
}
