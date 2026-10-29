package dev.mark.llm.service;

import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
import dev.mark.task.repository.LlmInteractionLogRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Primary
public class LlmRouterService implements LlmProviderService {

    private final GeminiLlmProviderService gemini;
    private final LlmSettingsService llmSettingsService;
    private final org.springframework.web.client.RestClient.Builder restClientBuilder;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final LlmInteractionLogRepository logRepository;
    
    private static final Pattern THINKING_PATTERN = Pattern.compile("(?s)<thinking>(.*?)</thinking>");

    public LlmRouterService(
            GeminiLlmProviderService gemini,
            LlmSettingsService llmSettingsService,
            org.springframework.web.client.RestClient.Builder restClientBuilder,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper,
            LlmInteractionLogRepository logRepository) {
        this.gemini = gemini;
        this.llmSettingsService = llmSettingsService;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.logRepository = logRepository;
    }

    @Override
    public String name() {
        return llmSettingsService.getDefaultConfig()
                .map(c -> c.getConfigName())
                .orElse("gemini");
    }

    @Override
    public PlanResponseDTO plan(LlmRequestDTO request) {
        PlanResponseDTO res = select(request).plan(request);
        saveLog(request, String.valueOf(res.steps()));
        return res;
    }

    @Override
    public PlanResponseDTO planStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        // We intercept the stream to capture the raw response because PlanResponseDTO drops the thinking
        StringBuilder fullStream = new StringBuilder();
        PlanResponseDTO res = select(request).planStream(request, chunk -> {
            fullStream.append(chunk);
            if (onChunk != null) onChunk.accept(chunk);
        });
        saveLog(request, fullStream.toString());
        return res;
    }

    @Override
    public LlmResponseDTO complete(LlmRequestDTO request) {
        LlmResponseDTO res = select(request).complete(request);
        saveLog(request, res != null ? res.content() : null);
        return res;
    }

    @Override
    public LlmResponseDTO completeStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        // We intercept the stream to capture the exact raw response in case the provider mutates it
        StringBuilder fullStream = new StringBuilder();
        LlmResponseDTO res = select(request).completeStream(request, chunk -> {
            fullStream.append(chunk);
            if (onChunk != null) onChunk.accept(chunk);
        });
        saveLog(request, fullStream.toString());
        return res;
    }

    private void saveLog(LlmRequestDTO req, String rawResponse) {
        if (req.taskId() != null) {
            String thought = "";
            String response = rawResponse;
            if (rawResponse != null) {
                Matcher m = THINKING_PATTERN.matcher(rawResponse);
                if (m.find()) {
                    thought = m.group(1).trim();
                    response = rawResponse.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
                } else if (rawResponse.contains("```json")) {
                    // Sometimes the thought is just outside the JSON block
                    int jsonStart = rawResponse.indexOf("```json");
                    thought = rawResponse.substring(0, jsonStart).trim();
                    response = rawResponse.substring(jsonStart).trim();
                } else if (req.phase() != null && req.phase().equals("Planning")) {
                    // For planning, maybe the entire thing is the thought if JSON failed, or mixed
                    thought = rawResponse;
                }
            }
            
            dev.mark.task.entity.LlmInteractionLogEntity log = new dev.mark.task.entity.LlmInteractionLogEntity(
                req.taskId(), 
                req.phase() != null ? req.phase() : "Unspecified", 
                req.userPrompt(), 
                thought, 
                response
            );
            logRepository.save(log);
        }
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
