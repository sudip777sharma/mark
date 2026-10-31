package dev.mark.tool.impl.desktop;

import org.junit.jupiter.api.Test;

import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;

class DesktopAutomationToolTest {

    @Test
    void testResolveKeyCodeSingleKeys() {
        DesktopAutomationTool tool = new DesktopAutomationTool();

        assertEquals(KeyEvent.VK_ENTER, tool.resolveKeyCode("ENTER"));
        assertEquals(KeyEvent.VK_ESCAPE, tool.resolveKeyCode("ESCAPE"));
        assertEquals(KeyEvent.VK_TAB, tool.resolveKeyCode("TAB"));
        assertEquals(KeyEvent.VK_CONTROL, tool.resolveKeyCode("CTRL"));
        assertEquals(KeyEvent.VK_CONTROL, tool.resolveKeyCode("CONTROL"));
        assertEquals(KeyEvent.VK_SHIFT, tool.resolveKeyCode("SHIFT"));
        assertEquals(KeyEvent.VK_ALT, tool.resolveKeyCode("ALT"));
        assertEquals(KeyEvent.VK_WINDOWS, tool.resolveKeyCode("WINDOWS"));
        assertEquals(KeyEvent.VK_F1, tool.resolveKeyCode("F1"));
        assertEquals(KeyEvent.VK_F5, tool.resolveKeyCode("F5"));
        assertEquals(KeyEvent.VK_A, tool.resolveKeyCode("A"));
        assertEquals(KeyEvent.VK_Z, tool.resolveKeyCode("Z"));
        assertEquals(KeyEvent.VK_0, tool.resolveKeyCode("0"));
        assertEquals(KeyEvent.VK_9, tool.resolveKeyCode("9"));
    }

    @Test
    void testResolveKeyCodeInvalidThrows() {
        DesktopAutomationTool tool = new DesktopAutomationTool();
        assertThrows(IllegalArgumentException.class, () -> tool.resolveKeyCode("INVALID_KEY_NAME_XYZ"));
    }
}
