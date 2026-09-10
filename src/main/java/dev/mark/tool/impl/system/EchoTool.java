package dev.mark.tool.impl.system;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * - What the class does: Implements the Tool interface to echo a provided input message back to the caller.
 * - Why it is useful: Provides a simple validation and debugging mechanism for testing tool execution flows.
 * - How it fits in the flow of the application: Acts as a registered Spring component invoked by the tool execution engine when the echo command is requested.
 * - Its methods and variables and how they are useful: name() identifies the tool, description() explains its purpose, parameterSchema() defines the expected input structure, and execute() processes the request and validates the message.
 * - Its logic and how it gets fit into the overall application logic: Extracts and validates the message argument from the request DTO, returning a failure result if invalid or a success result with the prefixed echo string to integrate seamlessly with the response pipeline.
 */

@Component
public class EchoTool implements Tool {
    public String name() { return "echo"; }
    public String description() { return "Echo a message exactly as provided."; }
    public Map<String, Object> parameterSchema() {
        return Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string", "description", "Message to echo")), "required", java.util.List.of("message"));
    }
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object messageArgument = request.arguments().get("message");
        if (!(messageArgument instanceof String message) || message.isBlank()) {
            return new ToolResultDTO(false, "echo requires a non-blank string message argument", Map.of());
        }
        return ToolResultDTO.success("Echoed: " + message);
    }
}
