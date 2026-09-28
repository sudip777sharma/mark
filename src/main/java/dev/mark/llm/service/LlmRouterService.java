package dev.mark.llm.service;

import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
        Long requested = request.configId();

        dev.mark.llm.entity.LlmProviderConfig config;
        
        if (requested == null) {
            config = llmSettingsService.getDefaultConfig()
                    .orElseThrow(() -> new LlmProviderException("No default LLM provider configured"));
        } else {
            config = llmSettingsService.getConfig(requested)
                    .orElseThrow(() -> new LlmProviderException("Unknown LLM provider ID: " + requested));
        }

        if ("GEMINI".equalsIgnoreCase(config.getProviderType())) {
            return gemini; 
        } else {
            String nextApiKey = llmSettingsService.getNextApiKey(config.getId());
            return new OpenAiCompatibleProviderService(
                    config.getId(),
                    config.getConfigName(),
                    config.getBaseUrl(),
                    nextApiKey,
                    config.getActiveModel(),
                    restClientBuilder,
                    objectMapper,
                    llmSettingsService
            );
        }
    }
}
