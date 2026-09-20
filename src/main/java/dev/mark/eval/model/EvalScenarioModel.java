package dev.mark.eval.model;

public record EvalScenarioModel(
    String id,
    String goal,
    String expectedFinalAnswerRegex
) {
}
