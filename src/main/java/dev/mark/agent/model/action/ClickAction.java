package dev.mark.agent.model.action;

public record ClickAction(
    String targetId,
    String button
) implements Action {
    @Override
    public String type() { return "click"; }
    @Override
    public boolean requiresTarget() { return true; }
}
