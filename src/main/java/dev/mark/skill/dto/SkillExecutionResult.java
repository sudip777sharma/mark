package dev.mark.skill.dto;

import dev.mark.agent.model.AgentStepModel;
import dev.mark.tool.dto.HumanInterventionRequest;

import java.util.List;

/**
 * Encapsulates the execution outcome of an L0 deterministic skill.
 */
public record SkillExecutionResult(
        boolean success,
        String summary,
        List<AgentStepModel> steps,
        HumanInterventionRequest intervention
) {
    public static SkillExecutionResult completed(String summary, List<AgentStepModel> steps) {
        return new SkillExecutionResult(true, summary, steps != null ? steps : List.of(), null);
    }

    public static SkillExecutionResult failed(String summary, List<AgentStepModel> steps) {
        return new SkillExecutionResult(false, summary, steps != null ? steps : List.of(), null);
    }

    public static SkillExecutionResult requiresIntervention(HumanInterventionRequest intervention, String summary, List<AgentStepModel> steps) {
        return new SkillExecutionResult(false, summary, steps != null ? steps : List.of(), intervention);
    }
}
