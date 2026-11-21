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

        Map<String, Object> args = request.arguments();
        String selector = (String) args.get("selector");
        if (selector == null || selector.isBlank()) {
            selector = (String) args.get("input_selector");
        }
        if (selector == null || selector.isBlank()) {
            selector = (String) args.get("input_field");
        }
        if (selector == null || selector.isBlank()) {
            selector = (String) args.get("field");
        }
        if (selector == null || selector.isBlank()) {
            selector = (String) args.get("element");
        }
        if (selector == null || selector.isBlank()) {
            selector = (String) args.get("target");
        }

        String text = (String) args.get("text");
        if (text == null) {
            text = (String) args.get("value");
        }
        if (text == null) {
            text = (String) args.get("input_value");
        }
        if (text == null) {
            text = (String) args.get("input");
        }
        if (text == null) {
            text = (String) args.get("content");
        }

        if (selector == null || selector.isBlank() || text == null) {
            return new ToolResultDTO(false, "Missing required arguments 'selector' or 'text'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            
            String targetSelector = selector;
            if (!selector.startsWith("#") && !selector.startsWith(".") && !selector.startsWith("[") && !selector.contains(" ")) {
                if (page.locator(selector).count() == 0) {
                    var alt = page.locator("input[name='" + selector + "'], input[id='" + selector + "'], input[placeholder*='" + selector + "' i], textarea[name='" + selector + "']");
                    if (alt.count() > 0) {
                        targetSelector = "input[name='" + selector + "'], input[id='" + selector + "'], input[placeholder*='" + selector + "' i], textarea[name='" + selector + "']";
                    }
                }
            }

            // Wait for element
            page.waitForSelector(targetSelector, new com.microsoft.playwright.Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            
            page.fill(targetSelector, text);
            
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
