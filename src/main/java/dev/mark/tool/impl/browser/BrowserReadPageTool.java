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
 * This class is a Spring-managed tool that extracts the title and all visible body text from the currently active browser page.
 *
 * **2. Why it is useful:**
 * It allows automated agents to scrape webpage content and verify if navigation or click actions succeeded, while safely truncating text to 15,000 characters to prevent LLM context window overflow.
 *
 * **3. How it fits in the flow of the application:**
 * It implements the Tool interface and is registered as a Spring component. It is executed dynamically by an orchestrator or LLM agent during browser automation tasks.
 *
 * **4. Methods and variables utility:**
 * - `browserSession`: Manages and provides access to the active browser page.
 * - `name()`: Returns "browser_read_page" for tool identification.
 * - `description()`: Explains the tool's purpose to the LLM.
 * - `execute()`: Performs the core extraction, truncation, and error handling.
 *
 * **5. Logic and integration:**
 * It first checks for an active page. If present, it executes a JavaScript evaluation to retrieve `document.body.innerText`, truncates the result if it exceeds 15,000 characters, and returns a structured ToolResultDTO to the calling application.
 */

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
    public ToolResultDTO execute(ToolRequestDTO request) {
        if (!browserSession.hasActivePage()) {
            return new ToolResultDTO(false, "No active browser page. Use browser_navigate first.", Map.of());
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

            return new ToolResultDTO(true, "Read page: " + title, Map.of("title", title, "content", content));
        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to read page: " + e.getMessage(), Map.of());
        }
    }
}
