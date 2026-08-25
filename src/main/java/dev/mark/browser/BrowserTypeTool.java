package dev.mark.browser;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
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
        return "Types text into an input field matching a CSS selector. Arguments: 'selector' (string), 'text' (string), 'press_enter' (optional boolean).";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResult(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        String selector = (String) request.arguments().get("selector");
        String text = (String) request.arguments().get("text");
        Boolean pressEnter = (Boolean) request.arguments().get("press_enter");
        
        if (selector == null || selector.isBlank()) {
            return new ToolResult(false, "Missing required argument 'selector'", Map.of());
        }
        if (text == null) {
            return new ToolResult(false, "Missing required argument 'text'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            page.fill(selector, text);
            String observation = "Typed text into element matching selector: " + selector;
            
            if (Boolean.TRUE.equals(pressEnter)) {
                page.press(selector, "Enter");
                observation += " and pressed Enter";
            }
            
            return new ToolResult(true, observation, Map.of());
        } catch (Exception e) {
            return new ToolResult(false, "Failed to type into element '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
