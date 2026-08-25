package dev.mark.browser;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserReadPageTool implements Tool {

    private final BrowserSession browserSession;

    public BrowserReadPageTool(BrowserSession browserSession) {
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "browser_read_page";
    }

    @Override
    public String description() {
        return "Reads all visible text on the current browser page. Use this to verify that your navigation or click actually worked, or to find information. No arguments required.";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResult(false, "No active browser page. Use browser_navigate first.", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            String title = page.title();
            // Extract the body innerText to get readable text
            String content = (String) page.evaluate("() => document.body.innerText");
            
            if (content == null) {
                content = "";
            }
            
            // Truncate to 15,000 chars to protect small context windows like Gemma 4B
            if (content.length() > 15000) {
                content = content.substring(0, 15000) + "... (truncated)";
            }

            return new ToolResult(true, "Read page: " + title, Map.of("title", title, "content", content));
        } catch (Exception e) {
            return new ToolResult(false, "Failed to read page: " + e.getMessage(), Map.of());
        }
    }
}
