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
class BrowserExtractToolTest {

    private BrowserSession browserSession;
    private BrowserNavigateTool navigateTool;
    private BrowserExtractTool extractTool;

    @BeforeEach
    void setUp() {
        browserSession = new BrowserSession(new BrowserProperties(true, 10000));
        browserSession.init();
        navigateTool = new BrowserNavigateTool(browserSession);
        extractTool = new BrowserExtractTool(browserSession);
    }

    @AfterEach
    void tearDown() {
        if (browserSession != null) {
            browserSession.close();
        }
    }

    @Test
    void extractsTextSuccessfully() {
        // Navigate first
        navigateTool.execute(new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of("url", "https://example.com")));
        
        // Then extract
        ToolRequest extractRequest = new ToolRequest(UUID.randomUUID(), "browser_extract", Map.of("selector", "h1"));
        ToolResult extractResult = extractTool.execute(extractRequest);

        assertTrue(extractResult.successful(), "Extraction should be successful");
        assertEquals("Example Domain", extractResult.metadata().get("text"), "Extracted text should match");
    }

    @Test
    void failsWhenNoActivePage() {
        // Try extracting without navigating
        ToolRequest extractRequest = new ToolRequest(UUID.randomUUID(), "browser_extract", Map.of("selector", "h1"));
        ToolResult extractResult = extractTool.execute(extractRequest);

        assertFalse(extractResult.successful(), "Extraction should fail when no active page");
        assertTrue(extractResult.observation().contains("No active browser page"), "Observation should indicate no active page");
    }
    
    @Test
    void defaultsToBodyWhenSelectorMissing() {
        // Navigate first
        navigateTool.execute(new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of("url", "https://example.com")));
        
        // Then extract without selector
        ToolRequest extractRequest = new ToolRequest(UUID.randomUUID(), "browser_extract", Map.of());
        ToolResult extractResult = extractTool.execute(extractRequest);

        assertTrue(extractResult.successful(), "Extraction should be successful with default selector");
        assertTrue(((String)extractResult.metadata().get("text")).contains("Example Domain"), "Extracted body text should contain the heading");
    }
}
