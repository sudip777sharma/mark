package dev.mark.tool.impl.browser;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.util.BrowserSession;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class BrowserTypeToolTest {

    @Test
    public void testNoActivePageReturnsFailure() {
        BrowserSession session = mock(BrowserSession.class);
        when(session.hasActivePage()).thenReturn(false);

        BrowserTypeTool tool = new BrowserTypeTool(session);
        ToolResultDTO result = tool.execute(new ToolRequestDTO(UUID.randomUUID(), "browser_type", Map.of("selector", "input", "text", "hello")));

        assertFalse(result.successful());
        assertTrue(result.observation().contains("No active browser page"));
    }

    @Test
    public void testMissingArgumentsFailsGracefully() {
        BrowserSession session = mock(BrowserSession.class);
        when(session.hasActivePage()).thenReturn(true);

        BrowserTypeTool tool = new BrowserTypeTool(session);
        ToolResultDTO result = tool.execute(new ToolRequestDTO(UUID.randomUUID(), "browser_type", Map.of()));

        assertFalse(result.successful());
        assertTrue(result.observation().contains("Missing required arguments"));
    }
}
