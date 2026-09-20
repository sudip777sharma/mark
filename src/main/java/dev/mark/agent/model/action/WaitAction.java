package dev.mark.agent.model.action;

public record WaitAction(
    int durationMs
) implements Action {
    @Override
    public String type() { return "wait"; }
    @Override
    public boolean requiresTarget() { return false; }
}
