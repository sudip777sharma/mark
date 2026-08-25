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
class BrowserNavigateToolTest {

    private BrowserSession browserSession;
    private BrowserNavigateTool navigateTool;

    @BeforeEach
    void setUp() {
        browserSession = new BrowserSession(new BrowserProperties(true, 10000));
        browserSession.init();
        navigateTool = new BrowserNavigateTool(browserSession);
    }

    @AfterEach
    void tearDown() {
        if (browserSession != null) {
            browserSession.close();
        }
    }

    @Test
    void navigatesSuccessfully() {
        ToolRequest request = new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of("url", "https://example.com"));
        ToolResult result = navigateTool.execute(request);

        assertTrue(result.successful(), "Navigation should be successful");
        assertTrue(result.observation().contains("Example Domain"), "Should contain page title in observation");
        assertEquals("Example Domain", result.metadata().get("title"), "Should return title in metadata");
        assertTrue(browserSession.hasActivePage(), "Should have active page after navigation");
    }

    @Test
    void failsOnInvalidUrl() {
        ToolRequest request = new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of("url", "not-a-valid-url"));
        ToolResult result = navigateTool.execute(request);

        assertFalse(result.successful(), "Navigation should fail for invalid URL");
        assertTrue(result.observation().contains("Failed to navigate"), "Observation should indicate failure");
    }
    
    @Test
    void failsOnMissingUrl() {
        ToolRequest request = new ToolRequest(UUID.randomUUID(), "browser_navigate", Map.of());
        ToolResult result = navigateTool.execute(request);

        assertFalse(result.successful(), "Navigation should fail when URL is missing");
        assertTrue(result.observation().contains("Missing required argument"), "Observation should indicate missing URL");
    }
}
