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
 * Implements a browser automation tool that clicks on a web page element matching a specified CSS selector.
 *
 * **2. Why it is useful:**
 * Enables automated user interactions (such as clicking buttons, links, or checkboxes) during automated browser sessions.
 *
 * **3. How it fits in the flow of the application:**
 * Acts as a pluggable Spring-managed tool component within the application's tool execution framework, invoked dynamically by an orchestrator or agent.
 *
 * **4. Its methods and variables and how they are useful:**
 * - `browserSession` (variable): Maintains the state of the active browser session and pages.
 * - `name()`, `description()`, `parameterSchema()` (methods): Define the tool's metadata and input requirements (requires a "selector" string).
 * - `execute()` (method): Performs the click action on the active page and returns the execution result.
 *
 * **5. Its logic and how it gets fit into the overall application logic:**
 * - Logic: Verifies an active browser page exists, extracts and validates the CSS selector, performs the click, and handles any runtime exceptions.
 * - Integration: Fits into the overall logic by executing sequential browser interaction steps requested by the client application.
 */

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
            page.click(selector);
            return new ToolResultDTO(true, "Clicked element matching selector: " + selector, Map.of());
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to click element '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
