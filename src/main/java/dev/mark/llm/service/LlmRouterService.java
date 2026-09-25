package dev.mark.llm.service;


import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

    /**
     * **Purpose**: Routes LLM requests to specific provider implementations.
     *
     * **Utility**: Centralizes provider selection and enables dynamic switching between multiple AI backends.
     *
     * **Application Flow**: Serves as the primary entry point in the service layer, intercepting requests before delegating them to a concrete provider.
     *
     * **Components**:
     * - Provider instances: Handle communication with specific LLM APIs (Gemini, Groq, etc.).
     * - activeProvider: Defines the default fallback provider from configuration.
     * - select(): Logic to resolve the target provider based on the request or default.
     * - plan() and complete(): Forward operations to the resolved provider.
     *
     * **Logic Integration**: Abstracts underlying API complexities behind a unified interface, allowing controllers to remain agnostic of the specific AI provider being used.
     */

/**
* Primary router service that delegates LLM requests to specific provider implementations.
* Useful for centralizing provider selection, supporting multiple backends, and enabling dynamic runtime switching.
* Acts as the main entry point in the service layer, intercepting incoming requests and routing them to the correct provider.
*
* Variables and Methods:
* - Provider instances (gemini, colab, local, groq, openrouter): Encapsulate communication logic for each specific backend.
* - activeProvider: Fallback provider name configured via application properties.
* - name(): Returns the identifier of the currently active default provider.
* - plan() and complete(): Execute LLM operations by forwarding requests to the selected provider.
* - select(): Evaluates the requested provider from the payload or falls back to the default active provider.
*
*/
@Component
@Primary
public class LlmRouterService implements LlmProviderService {

    private final GeminiLlmProviderService gemini;
    private final LlmSettingsService llmSettingsService;
    private final org.springframework.web.client.RestClient.Builder restClientBuilder;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public LlmRouterService(
            GeminiLlmProviderService gemini,
            LlmSettingsService llmSettingsService,
            org.springframework.web.client.RestClient.Builder restClientBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.gemini = gemini;
        this.llmSettingsService = llmSettingsService;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return llmSettingsService.getDefaultConfig()
                .map(c -> c.getConfigName())
                .orElse("gemini");
    }

    @Override
    public PlanResponseDTO plan(LlmRequestDTO request) {
        return select(request).plan(request);
    }

    @Override
    public LlmResponseDTO complete(LlmRequestDTO request) {
        return select(request).complete(request);
    }

    private LlmProviderService select(LlmRequestDTO request) {
        String requested = request.provider();

        if (requested == null || requested.isBlank() || requested.equals("auto")) {
            requested = name();
        }

        String finalRequested = requested;
        dev.mark.llm.entity.LlmProviderConfig config = llmSettingsService.getConfig(finalRequested)
                .orElseThrow(() -> new LlmProviderException("Unknown LLM provider: " + finalRequested));

        if ("GEMINI".equalsIgnoreCase(config.getProviderType())) {
            return gemini; // Gemini service already handles db key rotation internally
        } else {
            return new OpenAiCompatibleProviderService(
                    config.getConfigName(),
                    config.getBaseUrl(),
                    llmSettingsService.getNextApiKey(config.getConfigName()),
                    config.getActiveModel(),
                    restClientBuilder,
                    objectMapper
            );
        }
    }
}
