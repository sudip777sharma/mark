package dev.mark.agent.model.action;

public record TypeAction(
    String targetId,
    String text
) implements Action {
    @Override
    public String type() { return "type"; }
    @Override
    public boolean requiresTarget() { return true; }
}
