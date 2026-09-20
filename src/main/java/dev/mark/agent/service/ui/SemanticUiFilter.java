package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.SemanticObservationModel;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SemanticUiFilter {

    private static final Set<String> ACTIONABLE_ROLES = Set.of(
            "Button", "ListItem", "Edit", "Document", "Hyperlink", 
            "CheckBox", "ComboBox", "TabItem", "MenuItem", "RadioButton", 
            "Slider", "Thumb", "TreeItem"
    );

    private static final Set<String> INFORMATIVE_ROLES = Set.of(
            "Text", "Image"
    );

    public static SemanticObservationModel filter(UiTreeModel tree) {
        List<UiElementModel> actionableElements = new ArrayList<>();
        if (tree.root() != null) {
            traverseAndFilter(tree.root(), actionableElements);
        }
        return new SemanticObservationModel(
                tree.applicationName(),
                tree.windowTitle(),
                null, // Diff is injected later
                actionableElements
        );
    }

    private static void traverseAndFilter(UiElementModel node, List<UiElementModel> collected) {
        boolean isActionable = isElementActionable(node);
        List<String> actions = determineActions(node);

        if (isActionable) {
            // Create a copy without children to keep the output compact
            UiElementModel compactNode = new UiElementModel(
                    node.id(),
                    node.role(),
                    node.name(),
                    node.className(),
                    node.path(),
                    node.bounds(),
                    actions,
                    true,
                    null // Don't include children in the flattened semantic list
            );
            collected.add(compactNode);
        }

        if (node.children() != null) {
            for (UiElementModel child : node.children()) {
                traverseAndFilter(child, collected);
            }
        }
    }

    private static boolean isElementActionable(UiElementModel node) {
        String role = node.role();
        if (role == null) return false;
        
        if (ACTIONABLE_ROLES.contains(role)) {
            return true;
        }
        
        if (INFORMATIVE_ROLES.contains(role)) {
            return node.name() != null && !node.name().isBlank();
        }

        return false;
    }

    private static List<String> determineActions(UiElementModel node) {
        List<String> actions = new ArrayList<>();
        if (node.role() == null) return actions;

        String r = node.role();
        if (Set.of("Button", "ListItem", "Hyperlink", "CheckBox", "RadioButton", "MenuItem", "TabItem", "TreeItem").contains(r)) {
            actions.add("click");
        }
        if (Set.of("Edit", "Document", "ComboBox").contains(r)) {
            actions.add("type");
            actions.add("click");
        }
        
        return actions;
    }
}
