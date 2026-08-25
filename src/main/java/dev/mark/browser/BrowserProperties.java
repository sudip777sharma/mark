package dev.mark.browser;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mark.browser")
public record BrowserProperties(
        boolean headless,
        int timeoutMs
) {
}
