package dev.mark.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DesktopAutomationToolTest {

    @Test
    @DisabledIfEnvironmentVariable(named = "CI", matches = "true")
    void performsValidActions() {
        DesktopAutomationTool tool = new DesktopAutomationTool();

        ToolResult result = tool.execute(new ToolRequest(null, "desktop_automation", Map.of("action", "move", "x", 100, "y", 100)));
        assertTrue(result.successful());
        
        // Only simple logic test, avoid actual typing causing interference
        ToolResult typeResult = tool.execute(new ToolRequest(null, "desktop_automation", Map.of("action", "type", "text", "test")));
        assertTrue(typeResult.successful());
    }

    @Test
    void failsOnInvalidAction() {
        DesktopAutomationTool tool = new DesktopAutomationTool();
        ToolResult result = tool.execute(new ToolRequest(null, "desktop_automation", Map.of("action", "unknown")));
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Unknown action"));
    }
}
