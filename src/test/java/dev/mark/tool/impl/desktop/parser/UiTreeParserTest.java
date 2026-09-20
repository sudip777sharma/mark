package dev.mark.tool.impl.desktop.parser;

import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.model.ui.UiTreeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiTreeParserTest {

    @Test
    void testParseIndentedOutput() {
        String rawOutput = """
Title: Test Window
Window | Name='Test Window' | Class='Window' | Rect=[0,0,100,100]
  Group | Name='' | Class='Pane' | Rect=[10,10,90,90]
    Button | Name='OK' | Class='Btn' | Rect=[20,20,50,50]
    Button | Name='Cancel' | Class='Btn' | Rect=[70,20,50,50]
  Text | Name='Info' | Class='Txt' | Rect=[10,80,80,20]
""";

        UiTreeModel tree = UiTreeParser.parse(rawOutput, "App", "Test Window");
        assertEquals("App", tree.applicationName());
        assertEquals("Test Window", tree.windowTitle());

        UiElementModel root = tree.root();
        assertNotNull(root);
        assertEquals("Window", root.role());
        assertEquals("Test Window", root.name());
        
        assertEquals(2, root.children().size());
        
        UiElementModel group = root.children().get(0);
        assertEquals("Group", group.role());
        assertEquals(2, group.children().size());
        assertEquals("OK", group.children().get(0).name());
        assertEquals("Cancel", group.children().get(1).name());

        UiElementModel text = root.children().get(1);
        assertEquals("Text", text.role());
        assertEquals("Info", text.name());
        assertTrue(text.children().isEmpty());
    }
}
