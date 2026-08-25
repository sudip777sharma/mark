package dev.mark.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BrowserSession implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);

    private final BrowserProperties properties;
    private Playwright playwright;
    private Browser browser;
    private Page currentPage;

    public BrowserSession(BrowserProperties properties) {
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
