package dev.mark.tool.impl.system;

import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Interactive tool that pauses execution to request information, clarification, or approval from the user.
 * Supports flexible parameter keys ('question', 'message', 'prompt', 'input') for robust model compatibility.
 */
@Component
public class AskUserTool implements Tool {

    @Override
    public String name() {
        return "ask_user";
    }

    @Override
    public String description() {
        return "Pause execution and ask the user for information, clarification, or approval. Use this when the goal is ambiguous, you need a password/OTP, or you need explicit permission to proceed.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "question", Map.of(
                    "type", "string",
                    "description", "The question or clarification message to ask the user."
                ),
                "message", Map.of(
                    "type", "string",
                    "description", "Alternative parameter name for the question or message."
                )
            ),
            "required", List.of("question")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String q = extractQuestion(request.arguments());
        dev.mark.tool.dto.HumanInterventionRequest intervention = dev.mark.tool.dto.HumanInterventionRequest.clarification(
            "User Guidance Required",
            q,
            request.arguments() != null ? request.arguments() : Map.of()
        );
        return dev.mark.tool.dto.ToolResultDTO.intervention(intervention, "Execution paused. Waiting for user reply.", Map.of("question", q));
    }

    public static String extractQuestion(Map<String, Object> arguments) {
        if (arguments == null) return "MARK requires your guidance to proceed.";
        for (String key : List.of("question", "message", "prompt", "input", "clarification")) {
            if (arguments.get(key) instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        }
        return "MARK requires your guidance to proceed.";
    }
}
