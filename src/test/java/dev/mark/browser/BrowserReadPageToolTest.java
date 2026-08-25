package dev.mark.browser;

import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Tag("browser")
class BrowserReadPageToolTest {

    private BrowserSession session;
    private BrowserNavigateTool navigateTool;
    private BrowserReadPageTool readPageTool;

    @BeforeEach
    void setUp() {
        session = new BrowserSession(new BrowserProperties(true, 10000));
        session.init();
        navigateTool = new BrowserNavigateTool(session);
        readPageTool = new BrowserReadPageTool(session);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void readsPageSuccessfully() {
        // Navigate first
        navigateTool.execute(new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of("url", "https://example.com")));
        
        // Read page
        ToolRequest readRequest = new ToolRequest(UUID.randomUUID(), "browser_read_page", Map.of());
        ToolResult readResult = readPageTool.execute(readRequest);

        assertTrue(readResult.successful(), "Read page should be successful");
        assertNotNull(readResult.metadata().get("title"), "Should have title");
        assertTrue(readResult.metadata().get("title").toString().contains("Example Domain"), "Title should be Example Domain");
        
        String content = (String) readResult.metadata().get("content");
        assertNotNull(content, "Should have content");
        assertTrue(content.contains("Example Domain"), "Content should include heading");
    }

    @Test
    void failsWhenNoActivePage() {
        ToolRequest readRequest = new ToolRequest(UUID.randomUUID(), "browser_read_page", Map.of());
        ToolResult readResult = readPageTool.execute(readRequest);

        assertFalse(readResult.successful(), "Read page should fail without active page");
        assertTrue(readResult.observation().contains("No active browser page"), "Observation should complain about no active page");
    }
}
