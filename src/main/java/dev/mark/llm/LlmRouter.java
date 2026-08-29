package dev.mark.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class LlmRouter implements LlmProvider {

    private final GeminiLlmProvider gemini;

    private final OpenAiCompatibleProvider colab;
    private final OpenAiCompatibleProvider local;
    private final OpenAiCompatibleProvider groq;
    private final OpenAiCompatibleProvider openrouter;

    private final String activeProvider;

    public LlmRouter(
            GeminiLlmProvider gemini,

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

        this.colab = new OpenAiCompatibleProvider(
                "colab",
                colabBaseUrl,
                "",
                colabModel,
                restClientBuilder,
                objectMapper);

        this.local = new OpenAiCompatibleProvider(
                "local",
                localBaseUrl,
                "",
                localModel,
                restClientBuilder,
                objectMapper);

        this.groq = new OpenAiCompatibleProvider(
                "groq",
                groqBaseUrl,
                groqApiKey,
                groqModel,
                restClientBuilder,
                objectMapper);

        this.openrouter = new OpenAiCompatibleProvider(
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

    @Override
    public PlanResponse plan(LlmRequest request) {
        return select(request).plan(request);
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        return select(request).complete(request);
    }

    private LlmProvider select(LlmRequest request) {
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