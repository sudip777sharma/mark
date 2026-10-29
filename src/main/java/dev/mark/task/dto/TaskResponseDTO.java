package dev.mark.task.dto;

import dev.mark.agent.model.AgentStatusModel;
import dev.mark.agent.model.AgentStepModel;
import java.util.List;
import java.util.UUID;

public record TaskResponseDTO(
        UUID taskId,
        String goal,
        AgentStatusModel status,
        String finalAnswer,
        String currentAction,
        List<String> plan,
        int llmRequestCount,
        List<AgentStepModel> steps
) {
}
