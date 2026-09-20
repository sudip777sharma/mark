package dev.mark.eval.model;

import dev.mark.agent.model.AgentStatusModel;

public record EvalResultModel(
    String scenarioId,
    boolean passed,
    AgentStatusModel actualStatus,
    String actualFinalAnswer,
    String failureReason
) {
}
