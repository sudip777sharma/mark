package dev.mark.browser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("browser")
class BrowserSessionTest {

    private BrowserSession browserSession;

    @BeforeEach
    void setUp() {
        browserSession = new BrowserSession(new BrowserProperties(true, 10000));
        browserSession.init();
    }

    @AfterEach
    void tearDown() {
        if (browserSession != null) {
            browserSession.close();
        }
    }

    @Test
    void sessionInitializesCorrectly() {
        assertFalse(browserSession.hasActivePage(), "Should have no active page initially");
    }

    @Test
    void getOrCreatePageCreatesUsablePage() {
        var page = browserSession.getOrCreatePage();
        assertNotNull(page, "Page should not be null");
        assertTrue(browserSession.hasActivePage(), "Should have active page after creation");
        assertEquals(page, browserSession.getCurrentPage(), "getCurrentPage should return the created page");
    }
}
