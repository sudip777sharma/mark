package dev.mark.tool.util;

import dev.mark.tool.config.BrowserPropertiesConfig;
import com.microsoft.playwright.BrowserContext;
import java.nio.file.Paths;
import java.nio.file.Files;
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

    private final BrowserPropertiesConfig properties;
    private final dev.mark.preference.UserPreferenceService userPreferenceService;
    private Playwright playwright;
    private BrowserContext browserContext;
    private Page currentPage;

    public BrowserSession(dev.mark.preference.UserPreferenceService userPreferenceService, BrowserPropertiesConfig properties) {
        this.userPreferenceService = userPreferenceService;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        log.info("event=browser_init headless={} timeout={}", userPreferenceService.getBoolean("browser.headless", properties.headless()), userPreferenceService.getInt("browser.timeoutMs", properties.timeoutMs()));
        try {
            this.playwright = Playwright.create();
            
            String userDataDir = System.getProperty("user.home") + "/.mark/browser_data";
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(userDataDir));
            
            BrowserType.LaunchPersistentContextOptions pOptions = new BrowserType.LaunchPersistentContextOptions()
                    .setHeadless(userPreferenceService.getBoolean("browser.headless", properties.headless()));
            
            this.browserContext = playwright.chromium().launchPersistentContext(Paths.get(userDataDir), pOptions);
        } catch (Exception e) {
            log.error("event=browser_init_failed", e);
            throw new RuntimeException("Failed to initialize Playwright browser", e);
        }
    }

    public Page getOrCreatePage() {
        if (this.currentPage == null || this.currentPage.isClosed()) {
            log.debug("event=browser_new_page");
            this.currentPage = browserContext.pages().size() > 0 ? browserContext.pages().get(0) : browserContext.newPage();
            this.currentPage.setDefaultTimeout(userPreferenceService.getInt("browser.timeoutMs", properties.timeoutMs()));
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
        if (browserContext != null) {
            try { browserContext.close(); } catch (Exception ignored) {}
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
    }
}
