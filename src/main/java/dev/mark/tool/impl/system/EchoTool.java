package dev.mark.tool.impl.system;

import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * EchoTool is restricted to the "test" profile for internal verification.
 * It is excluded from the active production tool registry to prevent LLM tool confusion.
 */
@Component
@Profile("test")
public class EchoTool implements Tool {
    @Override public String name() { return "echo"; }
    @Override public String description() { return "Echo a message exactly as provided."; }
    @Override public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string", "description", "Message to echo")), "required", java.util.List.of("message"));
    }
    @Override public ToolResultDTO execute(ToolRequestDTO request) {
        Object messageArgument = request.arguments().get("message");
        if (!(messageArgument instanceof String message) || message.isBlank()) {
            return new ToolResultDTO(false, "echo requires a non-blank string message argument", Map.of());
        }
        return ToolResultDTO.success("Echoed: " + message);
    }
}
