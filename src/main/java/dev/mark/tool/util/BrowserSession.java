package dev.mark.tool.util;


import dev.mark.tool.config.BrowserPropertiesConfig;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages the lifecycle and state of a Playwright browser instance and its active page.
 *
 * - **Purpose**: Encapsulates browser initialization, page management, and teardown logic driven by application configuration properties.
 * - **Utility**: Simplifies automation tasks by providing lazy page creation, active state validation, and centralized resource handling.
 * - **Application Flow**: Acts as a Spring component that initializes automatically on startup and shuts down gracefully upon application termination.
 * - **Methods and Variables**:
 *   - properties: Holds configuration settings like headless mode and execution timeouts.
 *   - playwright, browser, currentPage: Core Playwright runtime references.
 *   - init(): Sets up the Playwright instance and launches the browser post-construction.
 *   - getOrCreatePage(): Lazily creates or reuses an active browser page.
 *   - getCurrentPage and hasActivePage: Expose current page status to consumers.
 *   - close: Cleans up and releases all underlying browser resources.
 * - **Application Logic Integration**: Serves as a central browser provider for services requiring web interactions, maintaining a managed single-session lifecycle.
 */

/**
 * Manages the lifecycle of a Playwright browser instance and its active page.
 *
 * - **Purpose**: Encapsulates browser automation setup, page management, and teardown logic using configuration properties.
 * - **Utility**: Simplifies web scraping or automation tasks by handling browser initialization, lazy page creation, and state checking.
 * - **Application Flow**: Acts as a managed Spring bean that starts automatically on application startup and shuts down gracefully upon termination.
 * - **Methods & Variables**:
 *   - properties: Configuration source for headless mode and timeouts.
 *   - playwright, browser, currentPage: Core Playwright handles for execution.
 *   - init(): Initializes Playwright and launches the browser post-construction.
 *   - getOrCreatePage(): Lazily instantiates or reuses a valid browser page.
 *   - getCurrentPage() / hasActivePage(): Exposes page state for consumers.
 *   - close(): Cleans up browser resources pre-destruction.
 * - **Logic Integration**: Fits into the application as a central utility provider for components requiring browser interactions, maintaining a single shared browsing session lifecycle.
 */

@Component
public class BrowserSession implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final BrowserPropertiesConfig properties;
    private Playwright playwright;
    private Browser browser;
    private Page currentPage;

    public BrowserSession(BrowserPropertiesConfig properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("event=browser_init headless={} timeout={}", properties.headless(), properties.timeoutMs());
        try {
            this.playwright = Playwright.create();
            BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                    .setHeadless(properties.headless());
            this.browser = playwright.chromium().launch(options);
        } catch (Exception e) {
            log.error("event=browser_init_failed", e);
            throw new RuntimeException("Failed to initialize Playwright browser", e);
        }
    }

    public Page getOrCreatePage() {
        if (this.currentPage == null || this.currentPage.isClosed()) {
            log.debug("event=browser_new_page");
            this.currentPage = browser.newPage();
            this.currentPage.setDefaultTimeout(properties.timeoutMs());
        }
        return this.currentPage;
    }

    public Page getCurrentPage() {
        return this.currentPage;
    }

    public boolean hasActivePage() {
        return this.currentPage != null && !this.currentPage.isClosed();
    }

    @PreDestroy
    @Override
    public void close() {
        log.info("event=browser_shutdown");
        if (currentPage != null) {
            try { currentPage.close(); } catch (Exception ignored) {}
        }
        if (browser != null) {
            try { browser.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
    }
}
