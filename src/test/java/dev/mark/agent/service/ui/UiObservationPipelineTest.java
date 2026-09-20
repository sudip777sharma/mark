package dev.mark.agent.service.ui;

import dev.mark.agent.model.ui.SemanticObservationModel;
import dev.mark.agent.model.ui.UiBoundingRectangle;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UiObservationPipelineTest {

    @Test
    void testIdentityGenerationAndSemanticFiltering() {
        // Mock a raw tree parsed from PowerShell
        UiElementModel root = new UiElementModel(
                null, "Window", "Calculator", "CalcFrame", null, new UiBoundingRectangle(0, 0, 500, 500), List.of(), false,
                List.of(
                        new UiElementModel(
                                null, "Group", "", "Pane", null, new UiBoundingRectangle(10, 10, 480, 480), List.of(), false,
                                List.of(
                                        new UiElementModel(
                                                null, "Button", "1", "Button", null, new UiBoundingRectangle(20, 20, 50, 50), List.of(), false, List.of()
                                        ),
                                        new UiElementModel(
                                                null, "Text", "Result", "Text", null, new UiBoundingRectangle(20, 100, 200, 50), List.of(), false, List.of()
                                        ),
                                        new UiElementModel(
                                                null, "Text", "", "Text", null, new UiBoundingRectangle(0,0,0,0), List.of(), false, List.of() // empty text, should be filtered
                                        )
                                )
                        )
                )
        );

        UiTreeModel rawTree = new UiTreeModel("WindowsApp", "Calculator", root);

        // 1. Generate Identities
        UiTreeModel identityTree = DeterministicIdentityGenerator.generateIdentities(rawTree);
        
        UiElementModel idRoot = identityTree.root();
        assertNotNull(idRoot.id());
        assertEquals("WindowsApp > Calculator", idRoot.path());

        UiElementModel group = idRoot.children().get(0);
        assertNotNull(group.id());
        assertEquals("WindowsApp > Calculator > Group", group.path());

        UiElementModel button1 = group.children().get(0);
        assertNotNull(button1.id());
        assertEquals("WindowsApp > Calculator > Group > 1", button1.path());

        // 2. Semantic Filter
        SemanticObservationModel semantic = SemanticUiFilter.filter(identityTree);
        assertEquals("WindowsApp", semantic.applicationName());
        assertEquals("Calculator", semantic.windowTitle());
        
        List<UiElementModel> elements = semantic.actionableElements();
        
        // Should only contain the Button and the named Text, ignoring the structural Group and empty Text
        assertEquals(2, elements.size());
        
        UiElementModel semButton = elements.get(0);
        assertEquals("Button", semButton.role());
        assertEquals("1", semButton.name());
        assertEquals("WindowsApp > Calculator > Group > 1", semButton.path());
        assertTrue(semButton.actions().contains("click"));
        assertNull(semButton.children()); // Children should be stripped for compact output
        
        UiElementModel semText = elements.get(1);
        assertEquals("Text", semText.role());
        assertEquals("Result", semText.name());
    }
}
