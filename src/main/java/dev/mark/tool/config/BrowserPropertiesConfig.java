package dev.mark.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

    /**
     * Configuration properties holder for browser settings.
     *
     * - **Purpose**: Binds external configuration properties prefixed with "mark.browser" to a type-safe Java record.
     * - **Utility**: Centralizes browser settings to eliminate hardcoded values and ensure maintainability.
     * - **Flow**: Loaded by Spring Boot during startup and injected into automation services to initialize browser instances.
     * - **Components**:
     *   - `headless()`: Boolean to toggle GUI-less mode for CI/CD environments.
     *   - `timeoutMs()`: Integer defining the maximum wait time for browser operations.
     * - **Logic**: Acts as an immutable DTO where Spring maps application.yml/properties values for use during browser setup.
     */

/**
 * Configuration properties holder for browser settings.
 *
 * 1. **What it does:** Binds external configuration properties prefixed with "mark.browser" to a type-safe Java record.
 * 2. **Why it is useful:** Eliminates hardcoded values, ensuring centralized, type-safe, and easily maintainable browser configuration.
 * 3. **Application Flow:** Loaded by Spring Boot during startup and injected into browser automation services (e.g., Selenium, Playwright) to initialize browser instances.
 * 4. **Variables & Methods:**
 *    - `headless()`: Returns a boolean indicating if the browser should run without a GUI (essential for headless CI/CD pipelines).
 *    - `timeoutMs()`: Returns the maximum wait time in milliseconds for browser operations to prevent hanging.
 * 5. **Logic & Integration:** Acts as an immutable data transfer object. Spring automatically maps properties from application.yml/properties into this record, which the application logic queries during browser setup.
 */

@ConfigurationProperties(prefix = "mark.browser")
public record BrowserPropertiesConfig(
        boolean headless,
        int timeoutMs
) {
}
