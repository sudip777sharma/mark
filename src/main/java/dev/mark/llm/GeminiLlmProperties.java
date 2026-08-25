package dev.mark.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("mark.llm.gemini")
public record GeminiLlmProperties(String apiKey, String model) {
    public GeminiLlmProperties {
        if (model == null || model.isBlank()) {
            model = "gemini-2.5-flash";
        }
    }
}
