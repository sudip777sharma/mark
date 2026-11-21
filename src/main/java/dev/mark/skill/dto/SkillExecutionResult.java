package dev.mark.skill.dto;

import dev.mark.agent.model.AgentStepModel;
import dev.mark.tool.dto.HumanInterventionRequest;

import java.util.List;
import java.util.Map;

/**
 * Encapsulates the execution outcome of an L0 deterministic skill.
 */
public record SkillExecutionResult(
        boolean success,
        String summary,
        List<AgentStepModel> steps,
        HumanInterventionRequest intervention,
        Map<String, Object> evidence
) {
    public SkillExecutionResult(boolean success, String summary, List<AgentStepModel> steps, HumanInterventionRequest intervention) {
        this(success, summary, steps, intervention, Map.of());
    }

    public SkillExecutionResult {
        steps = steps != null ? List.copyOf(steps) : List.of();
        evidence = evidence != null ? Map.copyOf(evidence) : Map.of();
    }

    public static SkillExecutionResult completed(String summary, List<AgentStepModel> steps) {
        return completed(summary, steps, Map.of());
    }

    public static SkillExecutionResult completed(String summary, List<AgentStepModel> steps, Map<String, Object> evidence) {
        return new SkillExecutionResult(true, summary, steps, null, evidence);
    }

    public static SkillExecutionResult failed(String summary, List<AgentStepModel> steps) {
        return new SkillExecutionResult(false, summary, steps, null, Map.of());
    }

    public static SkillExecutionResult failed(String summary, List<AgentStepModel> steps, Map<String, Object> evidence) {
        return new SkillExecutionResult(false, summary, steps, null, evidence);
    }

    public static SkillExecutionResult requiresIntervention(HumanInterventionRequest intervention, String summary, List<AgentStepModel> steps) {
        return new SkillExecutionResult(false, summary, steps, intervention, Map.of());
    }
}
