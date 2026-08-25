package dev.mark.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mark.agent")
public record AgentProperties(int maxSteps, Integer maxHistoryLength, Integer maxHistoryChars) {
    public AgentProperties {
        if (maxHistoryLength == null) {
            maxHistoryLength = 6;
        }
        if (maxHistoryChars == null) {
            maxHistoryChars = 20000;
        }
    }
}
