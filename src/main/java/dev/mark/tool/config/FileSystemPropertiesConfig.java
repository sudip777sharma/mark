package dev.mark.tool.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * - **What it does:** Binds and holds file system configuration properties prefixed with mark.filesystem.
 * - **Why it is useful:** Provides type-safe and centralized access to file paths, eliminating hardcoded strings and enabling environment-specific configurations.
 * - **Application Flow:** Loaded during Spring Boot application startup and injected directly into file-handling services.
 * - **Variables and Methods:**
 *   - basePath: Stores the root directory path for file operations.
 *   - Compact Constructor: Validates the basePath and applies a safe fallback if missing.
 * - **Logic and Integration:** Falls back to a default workspace directory if the provided path is null or blank, ensuring downstream services always receive a valid path and preventing runtime errors.
 */

/**
 * Configuration properties class for file system settings.
 *
 * - **What it does:** Binds and holds configuration properties prefixed with "mark.filesystem" from application configuration files.
 * - **Why it is useful:** Provides type-safe, centralized access to file system settings, avoiding hardcoded paths and enabling environment-specific configurations.
 * - **Application Flow:** Loaded during Spring Boot startup and injected into file-handling services to resolve storage paths.
 * - **Variables & Methods:**
 *   - `basePath`: Holds the root directory path for file operations.
 *   - `FileSystemPropertiesConfig` (Constructor): Validates the input and applies a default fallback path.
 * - **Logic & Integration:** If `basePath` is null or blank, the constructor defaults it to "./mark-workspace". This ensures downstream services always receive a valid, non-empty directory path, preventing runtime path errors.
 */

@ConfigurationProperties("mark.filesystem")
public record FileSystemPropertiesConfig(String basePath) {
    public FileSystemPropertiesConfig {
        if (basePath == null || basePath.isBlank()) basePath = "./mark-workspace";
    }
}
