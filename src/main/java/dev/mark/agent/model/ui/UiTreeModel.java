package dev.mark.agent.model.ui;

public record UiTreeModel(
        String applicationName,
        String windowTitle,
        UiElementModel root
) {
}
