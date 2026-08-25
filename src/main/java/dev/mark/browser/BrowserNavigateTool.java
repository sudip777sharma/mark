package dev.mark.browser;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserNavigateTool implements Tool {

    private final BrowserSession browserSession;

    public BrowserNavigateTool(BrowserSession browserSession) {
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "browser_navigate";
    }

    @Override
    public String description() {
        return "Navigates the browser to a given URL. Argument: 'url' (string). Use this first before interacting with a page.";
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String url = (String) request.arguments().get("url");
        if (url == null || url.isBlank()) {
            return new ToolResult(false, "Missing required argument 'url'", Map.of());
        }

        try {
            var page = browserSession.getOrCreatePage();
            page.navigate(url);
            
            // Sometimes sites redirect immediately after load, causing page.title() to fail
            // with "Execution context was destroyed". We try to wait a bit if it happens.
            String title;
            try {
                title = page.title();
            } catch (Exception e) {
                try {
                    Thread.sleep(1500);
                    title = page.title();
                } catch (Exception ex) {
                    title = "Unknown (Context destroyed during redirect)";
                }
            }
            
            return new ToolResult(true, "Navigated to " + url + ". Page title: " + title, Map.of("title", title));
        } catch (Exception e) {
            return new ToolResult(false, "Failed to navigate to " + url + ": " + e.getMessage(), Map.of());
        }
    }
}
