package dev.mark.agent.model.ui;

import java.util.List;

public record UiElementModel(
        String id,
        String role,
        String name,
        String className,
        String path,
        UiBoundingRectangle bounds,
        List<String> actions,
        boolean isActionable,
        List<UiElementModel> children
) {
    public UiElementModel withId(String newId) {
        return new UiElementModel(newId, role, name, className, path, bounds, actions, isActionable, children);
    }

    public UiElementModel withChildren(List<UiElementModel> newChildren) {
        return new UiElementModel(id, role, name, className, path, bounds, actions, isActionable, newChildren);
    }
}
