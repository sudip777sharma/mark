package dev.mark.task;

import dev.mark.agent.AgentStatus;
import dev.mark.agent.AgentStep;
import java.util.List;
import java.util.UUID;

public record TaskResponse(UUID taskId, String goal, AgentStatus status, String finalAnswer, List<String> plan, List<AgentStep> steps) { }
