package dev.mark.tool.impl.system;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * - Acts as an interactive tool that pauses execution to request information, clarification, or approval from the user.
 * - Essential for handling ambiguous goals, securing sensitive inputs like passwords or OTPs, and obtaining explicit permissions.
 * - Integrates into the workflow as a registered Spring component executed by the tool orchestration engine when user intervention is required.
 * - Implements the Tool interface providing metadata methods (name, description, parameterSchema) and the execute method to signal a paused state.
 * - Evaluates incoming requests through the execution flow, returning a result that halts automated processing until a user reply is provided.
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
                    "description", "The question to ask the user."
                )
            ),
            "required", java.util.List.of("question")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        return new ToolResultDTO(true, "Execution paused. Waiting for user reply.", Map.of());
    }
}
