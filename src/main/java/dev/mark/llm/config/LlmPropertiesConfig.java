package dev.mark.llm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * - **What the class does**: Immutable configuration record holding Gemini LLM property values bound from application properties using the prefix mark.llm.gemini.
 * - **Why it is useful**: Centralizes and type-safely manages LLM credentials and model configurations, falling back to a default model if none is provided.
 * - **How it fits in the flow of the application**: Loaded during Spring Boot initialization to supply necessary configuration data to LLM service clients.
 * - **Its methods and variables and how they are useful**: Contains apiKey and model fields for authentication and model selection, alongside a compact constructor enforcing a fallback default value for the model.
 * - **Its logic and how it gets fit into the overall application logic**: Acts as a foundational configuration data carrier that decouples hardcoded values from business logic, ensuring downstream LLM components always receive valid initialization parameters.
 */

@ConfigurationProperties("mark.llm.gemini")
public record LlmPropertiesConfig(String apiKey, String model) {
    public LlmPropertiesConfig {
        if (model == null || model.isBlank()) {
            model = "gemini-2.5-flash";
        }
    }
}
