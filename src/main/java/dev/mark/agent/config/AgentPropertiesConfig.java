package dev.mark.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * - Binds and manages externalized configuration properties prefixed with
 * "mark.agent".
 * - Centralizes agent execution limits and history constraints in a type-safe
 * manner to prevent invalid states.
 * - Populates automatically from application properties during the Spring Boot
 * startup flow.
 * - Contains record components (maxSteps, maxHistoryLength, maxHistoryChars)
 * and a compact constructor that enforces sensible default values when
 * properties are missing.
 * - Supplies strict operational and memory boundaries that downstream agent
 * execution and history management components rely on.
 */

@ConfigurationProperties("mark.agent")
public record AgentPropertiesConfig(int maxSteps, Integer maxHistoryLength, Integer maxHistoryChars) {
    public AgentPropertiesConfig {
        if (maxHistoryLength == null) {
            maxHistoryLength = 6;
        }
        if (maxHistoryChars == null) {
            maxHistoryChars = 20000;
        }
    }
}
