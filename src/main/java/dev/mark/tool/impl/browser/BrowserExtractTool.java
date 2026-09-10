package dev.mark.tool.impl.browser;

import dev.mark.tool.util.BrowserSession;
import dev.mark.tool.config.BrowserPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * **1. What it does:**
 * Extracts visible text from a webpage using a specified CSS selector (defaults to "body").
 *
 * **2. Why it is useful:**
 * Enables automated content scraping and data extraction from web pages while preventing token or memory overflow by truncating output to 4KB.
 *
 * **3. How it fits in the flow:**
 * Implements the Tool interface as a Spring @Component. It is executed sequentially after a browser session is initialized and navigated to a target URL.
 *
 * **4. Methods and variables:**
 * - MAX_TEXT_LENGTH: Constant (4096) that caps the extracted text size.
 * - browserSession: Manages the active browser instance.
 * - name(), description(), parameterSchema(): Provide metadata for tool discovery.
 * - execute(): Validates the session, extracts text, handles truncation, and returns the result.
 *
 * **5. Logic and overall integration:**
 * The class checks for an active browser page, retrieves the CSS selector from the request, extracts the text content, truncates it if it exceeds 4096 characters, and returns a ToolResultDTO. This integrates with the application's agentic workflow to feed web data back to the caller.
 */
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
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "selector", Map.of("type", "string", "description", "CSS selector of the element to extract text from (defaults to 'body')")
            )
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResultDTO(false, "No active browser page. Use browser_navigate first.", Map.of());
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

            return new ToolResultDTO(true, "Extracted text: " + text, Map.of("text", text != null ? text : ""));
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to extract text from '" + selector + "': " + e.getMessage(), Map.of());
        }
    }
}
