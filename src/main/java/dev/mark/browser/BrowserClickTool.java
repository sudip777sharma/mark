package dev.mark.browser;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
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
    public ToolResult execute(ToolRequest request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResult(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        String selector = (String) request.arguments().get("selector");
        if (selector == null || selector.isBlank()) {
            return new ToolResult(false, "Missing required argument 'selector'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            page.click(selector);
            return new ToolResult(true, "Clicked element matching selector: " + selector, Map.of());
        } catch (Exception e) {
            return new ToolResult(false, "Failed to click element '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
