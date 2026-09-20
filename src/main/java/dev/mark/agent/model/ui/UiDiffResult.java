package dev.mark.agent.model.ui;

import java.util.List;

public record UiDiffResult(
    boolean isStable,
    List<String> elementsAdded,
    List<String> elementsRemoved,
    List<String> propertiesChanged
) {}
