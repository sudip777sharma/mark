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
                    .setHeadless(userPreferenceService.getBoolean("browser.headless", properties.headless()))
                    .setArgs(java.util.List.of("--start-maximized", "--window-position=0,0", "--no-sandbox"))
                    .setViewportSize(null);
            
            this.browserContext = playwright.chromium().launchPersistentContext(Paths.get(userDataDir), pOptions);
        } catch (Exception e) {
            log.error("event=browser_init_failed", e);
            throw new RuntimeException("Failed to initialize Playwright browser", e);
        }
    }

    public synchronized void reset() {
        log.info("event=browser_reset");
        if (currentPage != null) {
            try { currentPage.close(); } catch (Exception ignored) {}
            currentPage = null;
        }
        if (browserContext != null) {
            try { browserContext.close(); } catch (Exception ignored) {}
            browserContext = null;
        }
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
            playwright = null;
        }
        init();
    }

    public synchronized Page getOrCreatePage() {
        try {
            if (this.browserContext == null) {
                init();
            }
            if (this.currentPage == null || this.currentPage.isClosed()) {
                log.debug("event=browser_new_page");
                java.util.List<Page> pages = browserContext.pages();
                this.currentPage = !pages.isEmpty() ? pages.get(0) : browserContext.newPage();
                this.currentPage.setDefaultTimeout(userPreferenceService.getInt("browser.timeoutMs", properties.timeoutMs()));
            }
            if (this.currentPage != null && !this.currentPage.isClosed()) {
                try {
                    this.currentPage.bringToFront();
                } catch (Exception ignored) {}
            }
            return this.currentPage;
        } catch (Exception e) {
            log.warn("event=browser_page_failed_reconnecting error={}", e.getMessage());
            reset();
            java.util.List<Page> pages = browserContext.pages();
            this.currentPage = !pages.isEmpty() ? pages.get(0) : browserContext.newPage();
            this.currentPage.setDefaultTimeout(userPreferenceService.getInt("browser.timeoutMs", properties.timeoutMs()));
            if (this.currentPage != null && !this.currentPage.isClosed()) {
                try {
                    this.currentPage.bringToFront();
                } catch (Exception ignored) {}
            }
            return this.currentPage;
        }
    }

    public Page getCurrentPage() {
        return this.currentPage;
    }

    public boolean hasActivePage() {
        try {
            return this.currentPage != null && !this.currentPage.isClosed();
        } catch (Exception ignored) {
            return false;
        }
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
