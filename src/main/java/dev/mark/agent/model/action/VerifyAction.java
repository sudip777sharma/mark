package dev.mark.agent.model.action;

public record VerifyAction(
    String targetId,
    String expectedState,
    Integer timeoutMs
) implements Action {
    @Override
    public String type() { return "verify"; }
    @Override
    public boolean requiresTarget() { return true; }
}
