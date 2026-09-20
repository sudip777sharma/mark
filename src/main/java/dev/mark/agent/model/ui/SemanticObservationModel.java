package dev.mark.agent.model.ui;

import java.util.List;

public record SemanticObservationModel(
        String applicationName,
        String windowTitle,
        UiDiffResult diffFromPrevious,
        List<UiElementModel> actionableElements
) {
}
