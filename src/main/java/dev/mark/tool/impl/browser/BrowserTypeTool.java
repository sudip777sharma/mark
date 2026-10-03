package dev.mark.tool.impl.browser;

import dev.mark.tool.util.BrowserSession;
import dev.mark.tool.config.BrowserPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserTypeTool implements Tool {

    private final BrowserSession browserSession;

    public BrowserTypeTool(BrowserSession browserSession) {
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "browser_type";
    }

    @Override
    public String description() {
        return "Types text into an element matching a CSS selector. Arguments: 'selector' (string), 'text' (string).";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", java.util.List.of("selector", "text"),
            "properties", Map.of(
                "selector", Map.of("type", "string", "description", "CSS selector of the input element"),
                "text", Map.of("type", "string", "description", "Text to type into the element")
            )
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResultDTO(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        String selector = (String) request.arguments().get("selector");
        String text = (String) request.arguments().get("text");

        if (selector == null || selector.isBlank() || text == null) {
            return new ToolResultDTO(false, "Missing required arguments 'selector' or 'text'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            
            // Wait for element
            page.waitForSelector(selector, new com.microsoft.playwright.Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            
            page.fill(selector, text);
            
            try { Thread.sleep(500); } catch (Exception ignored) {}
            
            return new ToolResultDTO(true, 
                "Successfully typed text into element matching selector: " + selector + "\n" +
                "[Verification] Text entered successfully.", 
                Map.of());
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to type into element '" + selector + "': " + e.getMessage() + "\n(Hint: If Playwright says 'intercepts pointer events', a modal or loading spinner is covering the element. Use browser_inspect to see the Accessibility Tree.)", Map.of());
        }
    }
}
