package dev.mark.tool.impl.browser;


import dev.mark.tool.util.BrowserSession;
import dev.mark.tool.config.BrowserPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * **1. What the class does:**
 * This class is a Spring-managed tool that navigates a browser instance to a specified URL.
 *
 * **2. Why it is useful:**
 * It provides the entry point for web-automation tasks, allowing the application to load a target webpage before performing subsequent interactions (like scraping or clicking).
 *
 * **3. How it fits in the flow of the application:**
 * It implements the `Tool` interface, allowing it to be registered in a tool-execution registry and invoked dynamically by an orchestrator (such as an AI agent) when web navigation is required.
 *
 * **4. Methods and variables utility:**
 * - `browserSession` (variable): Manages the active browser page lifecycle.
 * - `name()`, `description()`, `parameterSchema()` (methods): Expose the tool's metadata and input requirements (the "url" parameter) to the system.
 * - `execute()` (method): Validates the input, performs the navigation, and returns the execution status.
 *
 * **5. Logic and integration:**
 * The class extracts the URL from the request, retrieves or creates a browser page via `browserSession`, and navigates to the destination. It includes retry logic (a short sleep) to handle immediate page redirects that might otherwise cause context-destruction errors when fetching the page title, returning a structured `ToolResultDTO` to the caller.
 */

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
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", java.util.List.of("url"),
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "URL to navigate to"
                )
            )
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        String url = (String) request.arguments().get("url");
        if (url == null || url.isBlank()) {
            return new ToolResultDTO(false, "Missing required argument 'url'", Map.of());
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

            return new ToolResultDTO(true, "Navigated to " + url + ". Page title: " + title, Map.of("title", title));
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to navigate to " + url + ": " + e.getMessage(), Map.of());
        }
    }
}
