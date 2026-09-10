package dev.mark.tool.impl.system;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * - Represents a system tool used by the AI agent to signal the successful completion of a user goal.
 * - Essential for allowing the agent to explicitly terminate its execution loop and provide a final summary.
 * - Acts as an endpoint in the tool execution lifecycle, invoked when the agent determines the task is finished.
 * - Methods include name, description, parameterSchema, and execute, which validate and process the required summary argument.
 * - Integrates into the overarching agent logic by returning a success result that the AgentOrchestratorService intercepts to stop further processing.
 */

@Component
public class TaskCompleteTool implements Tool {

    @Override
    public String name() {
        return "task_complete";
    }

    @Override
    public String description() {
        return "Call this tool to indicate that you have successfully completed the user's goal. Argument: 'summary' (string) detailing what was accomplished.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", java.util.List.of("summary"),
            "properties", Map.of(
                "summary", Map.of(
                    "type", "string",
                    "description", "A summary of what you did to complete the goal."
                )
            )
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String summary = (String) request.arguments().get("summary");
        if (summary == null || summary.isBlank()) {
            return new ToolResultDTO(false, "Missing required argument 'summary'", Map.of());
        }

        // The AgentOrchestratorService will see this success message and should terminate the loop
        return new ToolResultDTO(true, "Goal successfully completed! Summary: " + summary, Map.of("summary", summary));
    }
}
