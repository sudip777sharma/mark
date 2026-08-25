package dev.mark.tool;

import org.springframework.stereotype.Component;

import java.util.Map;

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
    public ToolResult execute(ToolRequest request) {
        return new ToolResult(true, "Execution paused. Waiting for user reply.", Map.of());
    }
}
