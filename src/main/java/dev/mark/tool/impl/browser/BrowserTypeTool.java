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
 * Implements a browser automation tool that types text into a specified input field on the active web page.
 *
 * **2. Why it is useful:**
 * Enables automated interaction with web forms, search bars, and login screens by simulating user keyboard input.
 *
 * **3. How it fits in the flow of the application:**
 * Acts as a pluggable component in a tool-execution framework that interacts with an active BrowserSession.
 *
 * **4. Its methods and variables and how they are useful:**
 * - browserSession: Holds the active browser state to retrieve the current page.
 * - name() / description() / parameterSchema(): Expose metadata and input requirements to the orchestrator.
 * - execute(): Performs the core action of validating inputs, filling the text, optionally pressing Enter, and returning the execution result.
 *
 * **5. Its logic and how it gets fit into the overall application logic:**
 * Verifies an active browser page exists, extracts and validates arguments (selector, text, press_enter), interacts with the browser page to fill the element, optionally triggers an "Enter" keypress, and returns a success/failure status to the calling orchestrator.
 */

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
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "required", java.util.List.of("selector", "text"),
            "properties", Map.of(
                "selector", Map.of("type", "string", "description", "CSS selector of the input field"),
                "text", Map.of("type", "string", "description", "Text to type into the field"),
                "press_enter", Map.of("type", "boolean", "description", "True to press Enter after typing")
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
        Boolean pressEnter = (Boolean) request.arguments().get("press_enter");

        if (selector == null || selector.isBlank()) {
            return new ToolResultDTO(false, "Missing required argument 'selector'", Map.of());
        }
        if (text == null) {
            return new ToolResultDTO(false, "Missing required argument 'text'", Map.of());
        }

        try {
            var page = browserSession.getCurrentPage();
            page.fill(selector, text);
            String observation = "Typed text into element matching selector: " + selector;

            if (Boolean.TRUE.equals(pressEnter)) {
                page.press(selector, "Enter");
                observation += " and pressed Enter";
            }

            return new ToolResultDTO(true, observation, Map.of());
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to type into element '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
