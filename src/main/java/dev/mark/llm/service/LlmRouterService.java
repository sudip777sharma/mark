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
* Integrates into the application logic by abstracting underlying LLM APIs behind a unified interface, allowing controllers to process requests without knowing the concrete AI provider implementation.
*/

@Component
@Primary
public class LlmRouterService implements LlmProviderService {

    private final GeminiLlmProviderService gemini;

    private final OpenAiCompatibleProviderService colab;
    private final OpenAiCompatibleProviderService local;
    private final OpenAiCompatibleProviderService groq;
    private final OpenAiCompatibleProviderService openrouter;

    private volatile String activeProvider;

    public LlmRouterService(
            GeminiLlmProviderService gemini,

            org.springframework.web.client.RestClient.Builder restClientBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,

            @Value("${mark.llm.active-provider:colab}") String activeProvider,

            @Value("${mark.llm.colab.base-url:}") String colabBaseUrl,
            @Value("${mark.llm.colab.model:qwen2.5vl:7b}") String colabModel,

            @Value("${mark.llm.local.base-url:http://127.0.0.1:8080}") String localBaseUrl,
            @Value("${mark.llm.local.model:local-model}") String localModel,

            @Value("${mark.llm.groq.base-url:}") String groqBaseUrl,
            @Value("${mark.llm.groq.api-key:}") String groqApiKey,
            @Value("${mark.llm.groq.model:}") String groqModel,

            @Value("${mark.llm.openrouter.base-url:}") String openrouterBaseUrl,
            @Value("${mark.llm.openrouter.api-key:}") String openrouterApiKey,
            @Value("${mark.llm.openrouter.model:}") String openrouterModel) {

        this.gemini = gemini;
        this.activeProvider = activeProvider;

        this.colab = new OpenAiCompatibleProviderService(
                "colab",
                colabBaseUrl,
                "",
                colabModel,
                restClientBuilder,
                objectMapper);

        this.local = new OpenAiCompatibleProviderService(
                "local",
                localBaseUrl,
                "",
                localModel,
                restClientBuilder,
                objectMapper);

        this.groq = new OpenAiCompatibleProviderService(
                "groq",
                groqBaseUrl,
                groqApiKey,
                groqModel,
                restClientBuilder,
                objectMapper);

        this.openrouter = new OpenAiCompatibleProviderService(
                "openrouter",
                openrouterBaseUrl,
                openrouterApiKey,
                openrouterModel,
                restClientBuilder,
                objectMapper);
    }

    @Override
    public String name() {
        return activeProvider;
    }

    public String getActiveProvider() {
        return activeProvider;
    }

    public void setActiveProvider(String activeProvider) {
        if (activeProvider != null && !activeProvider.isBlank()) {
            this.activeProvider = activeProvider.trim().toLowerCase();
        }
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

        if (requested == null || requested.isBlank()) {
            requested = activeProvider;
        }

        return switch (requested.toLowerCase()) {
            case "colab" -> colab;
            case "local" -> local;
            case "gemini" -> gemini;
            case "groq" -> groq;
            case "openrouter" -> openrouter;
            default -> throw new LlmProviderException(
                    "Unknown LLM provider: " + requested);
        };
    }
}
