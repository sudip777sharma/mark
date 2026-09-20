package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.UiBoundingRectangle;
import dev.mark.agent.model.ui.UiDiffResult;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiTreeDifferTest {

    @Test
    void testDiff() {
        UiElementModel a = new UiElementModel("ui-1", "Button", "A", "Path", "Btn", new UiBoundingRectangle(0,0,10,10), List.of(), true, List.of());
        UiElementModel b = new UiElementModel("ui-2", "Button", "B", "Path", "Btn", new UiBoundingRectangle(10,10,10,10), List.of(), true, List.of());
        
        UiElementModel root1 = new UiElementModel("ui-root", "Window", "App", "Path", "Win", new UiBoundingRectangle(0,0,100,100), List.of(), true, List.of(a, b));
        UiTreeModel tree1 = new UiTreeModel("App", "App", root1);

        // Remove A, modify B, add C
        UiElementModel bModified = new UiElementModel("ui-2", "Button", "B_CHANGED", "Path", "Btn", new UiBoundingRectangle(10,10,10,10), List.of(), true, List.of());
        UiElementModel c = new UiElementModel("ui-3", "Button", "C", "Path", "Btn", new UiBoundingRectangle(20,20,10,10), List.of(), true, List.of());
        
        UiElementModel root2 = new UiElementModel("ui-root", "Window", "App", "Path", "Win", new UiBoundingRectangle(0,0,100,100), List.of(), true, List.of(bModified, c));
        UiTreeModel tree2 = new UiTreeModel("App", "App", root2);

        UiDiffResult result = UiTreeDiffer.diff(tree1, tree2);
        
        assertFalse(result.isStable());
        assertTrue(result.elementsAdded().contains("ui-3"));
        assertTrue(result.elementsRemoved().contains("ui-1"));
        assertTrue(result.propertiesChanged().contains("ui-2"));
    }
}
