package dev.mark.browser;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserExtractTool implements Tool {

    private static final int MAX_TEXT_LENGTH = 4096; // 4 KB limit
    private final BrowserSession browserSession;

    public BrowserExtractTool(BrowserSession browserSession) {
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "browser_extract";
    }

    @Override
    public String description() {
        return "Extracts visible text from the page. Argument: 'selector' (string, defaults to 'body'). Result is truncated to 4096 chars.";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResult(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        String selector = (String) request.arguments().getOrDefault("selector", "body");
        if (selector == null || selector.isBlank()) {
            selector = "body";
        }

        try {
            var page = browserSession.getCurrentPage();
            String text = page.textContent(selector);
            
            if (text != null && text.length() > MAX_TEXT_LENGTH) {
                text = text.substring(0, MAX_TEXT_LENGTH) + "... [truncated]";
            }
            
            return new ToolResult(true, "Extracted text: " + text, Map.of("text", text != null ? text : ""));
        } catch (Exception e) {
            return new ToolResult(false, "Failed to extract text from '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
