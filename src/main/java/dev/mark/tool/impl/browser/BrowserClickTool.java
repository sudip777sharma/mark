package dev.mark.tool.impl.browser;


import dev.mark.tool.util.BrowserSession;
import dev.mark.tool.config.BrowserPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserClickTool implements Tool {

    private final BrowserSession browserSession;

    public BrowserClickTool(BrowserSession browserSession) {
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "browser_click";
    }

    @Override
    public String description() {
        return "Clicks an element on the current page matching a CSS selector. Argument: 'selector' (string).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", java.util.List.of("selector"),
            "properties", Map.of(
                "selector", Map.of(
                    "type", "string",
                    "description", "CSS selector of the element to click"
                )
            )
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResultDTO(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        String selector = (String) request.arguments().get("selector");
        if (selector == null || selector.isBlank()) {
            return new ToolResultDTO(false, "Missing required argument 'selector'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            
            // Wait for element to be visible
            page.waitForSelector(selector, new com.microsoft.playwright.Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            
            page.click(selector);
            
            // Brief wait for any immediate UI reactions
            try { Thread.sleep(1000); } catch (Exception ignored) {}
            
            String postUrl = page.url();
            String title = page.title();
            
            return new ToolResultDTO(true, 
                "Successfully clicked element matching selector: " + selector + "\n" +
                "[Verification] Current URL: " + postUrl + "\n" +
                "[Verification] Page Title: " + title + "\n" +
                "(If the URL or Title didn't change as expected, the click might have been intercepted by a modal. Use browser_inspect to check.)", 
                Map.of());
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to click element '" + selector + "': " + e.getMessage() + "\n(Hint: If Playwright says 'intercepts pointer events', a modal or loading spinner is covering the element. Use browser_inspect to see the Accessibility Tree and find the blocking element.)", Map.of());
        }
    }
}
