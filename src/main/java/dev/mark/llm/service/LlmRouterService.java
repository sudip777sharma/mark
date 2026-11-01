package dev.mark.llm.service;

import dev.mark.llm.dto.*;
import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.exception.LlmProviderException;
import dev.mark.task.entity.LlmInteractionLogEntity;
import dev.mark.task.repository.LlmInteractionLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Primary
public class LlmRouterService implements LlmProviderService {

    private static final Logger log = LoggerFactory.getLogger(LlmRouterService.class);

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
                .map(LlmProviderConfig::getConfigName)
                .orElse("gemini");
    }

    @Override
    public PlanResponseDTO plan(LlmRequestDTO request) {
        LlmProviderConfig initialConfig = resolveInitialConfig(request);
        List<LlmProviderConfig> configsToTry = new ArrayList<>();
        if (initialConfig != null) {
            configsToTry.add(initialConfig);
        }
        configsToTry.addAll(llmSettingsService.getAvailableFallbackConfigs(initialConfig != null ? initialConfig.getId() : null));

        LlmProviderException lastException = null;
        for (LlmProviderConfig config : configsToTry) {
            int attempts = Math.max(1, llmSettingsService.getApiKeysCount(config.getId()));
            for (int i = 0; i < attempts; i++) {
                try {
                    LlmProviderService provider = createProviderService(config);
                    PlanResponseDTO res = provider.plan(request);
                    saveLog(request, String.valueOf(res.steps()));
                    return res;
                } catch (LlmProviderException e) {
                    lastException = e;
                    log.warn("Provider '{}' plan attempt {}/{} failed: {}", config.getConfigName(), i + 1, attempts, e.getMessage());
                    if (!isRetryableError(e)) {
                        throw e;
                    }
                }
            }
            log.warn("Exhausted active keys for provider '{}' during planning. Cascading to next available provider...", config.getConfigName());
        }

        throw lastException != null ? lastException : new LlmProviderException("All configured LLM providers failed for planning");
    }

    @Override
    public PlanResponseDTO planStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        LlmProviderConfig initialConfig = resolveInitialConfig(request);
        List<LlmProviderConfig> configsToTry = new ArrayList<>();
        if (initialConfig != null) {
            configsToTry.add(initialConfig);
        }
        configsToTry.addAll(llmSettingsService.getAvailableFallbackConfigs(initialConfig != null ? initialConfig.getId() : null));

        LlmProviderException lastException = null;
        for (LlmProviderConfig config : configsToTry) {
            int attempts = Math.max(1, llmSettingsService.getApiKeysCount(config.getId()));
            for (int i = 0; i < attempts; i++) {
                try {
                    LlmProviderService provider = createProviderService(config);
                    StringBuilder fullStream = new StringBuilder();
                    PlanResponseDTO res = provider.planStream(request, chunk -> {
                        fullStream.append(chunk);
                        if (onChunk != null) onChunk.accept(chunk);
                    });
                    saveLog(request, fullStream.toString());
                    return res;
                } catch (LlmProviderException e) {
                    lastException = e;
                    log.warn("Provider '{}' planStream attempt {}/{} failed: {}", config.getConfigName(), i + 1, attempts, e.getMessage());
                    if (!isRetryableError(e)) {
                        throw e;
                    }
                }
            }
            log.warn("Exhausted active keys for provider '{}' during planning. Cascading to next available provider...", config.getConfigName());
        }

        throw lastException != null ? lastException : new LlmProviderException("All configured LLM providers failed for planning");
    }

    @Override
    public LlmResponseDTO complete(LlmRequestDTO request) {
        LlmProviderConfig initialConfig = resolveInitialConfig(request);
        List<LlmProviderConfig> configsToTry = new ArrayList<>();
        if (initialConfig != null) {
            configsToTry.add(initialConfig);
        }
        configsToTry.addAll(llmSettingsService.getAvailableFallbackConfigs(initialConfig != null ? initialConfig.getId() : null));

        LlmProviderException lastException = null;
        for (LlmProviderConfig config : configsToTry) {
            int attempts = Math.max(1, llmSettingsService.getApiKeysCount(config.getId()));
            for (int i = 0; i < attempts; i++) {
                try {
                    LlmProviderService provider = createProviderService(config);
                    LlmResponseDTO res = provider.complete(request);
                    saveLog(request, res != null ? res.content() : null);
                    return res;
                } catch (LlmProviderException e) {
                    lastException = e;
                    log.warn("Provider '{}' complete attempt {}/{} failed: {}", config.getConfigName(), i + 1, attempts, e.getMessage());
                    if (!isRetryableError(e)) {
                        throw e;
                    }
                }
            }
            log.warn("Exhausted active keys for provider '{}'. Cascading to next available provider...", config.getConfigName());
        }

        throw lastException != null ? lastException : new LlmProviderException("All configured LLM providers failed");
    }

    @Override
    public LlmResponseDTO completeStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        LlmProviderConfig initialConfig = resolveInitialConfig(request);
        List<LlmProviderConfig> configsToTry = new ArrayList<>();
        if (initialConfig != null) {
            configsToTry.add(initialConfig);
        }
        configsToTry.addAll(llmSettingsService.getAvailableFallbackConfigs(initialConfig != null ? initialConfig.getId() : null));

        LlmProviderException lastException = null;
        for (LlmProviderConfig config : configsToTry) {
            int attempts = Math.max(1, llmSettingsService.getApiKeysCount(config.getId()));
            for (int i = 0; i < attempts; i++) {
                try {
                    LlmProviderService provider = createProviderService(config);
                    StringBuilder fullStream = new StringBuilder();
                    LlmResponseDTO res = provider.completeStream(request, chunk -> {
                        fullStream.append(chunk);
                        if (onChunk != null) onChunk.accept(chunk);
                    });
                    saveLog(request, fullStream.toString());
                    return res;
                } catch (LlmProviderException e) {
                    lastException = e;
                    log.warn("Provider '{}' completeStream attempt {}/{} failed: {}", config.getConfigName(), i + 1, attempts, e.getMessage());
                    if (!isRetryableError(e)) {
                        throw e;
                    }
                }
            }
            log.warn("Exhausted active keys for provider '{}'. Cascading to next available provider...", config.getConfigName());
        }

        throw lastException != null ? lastException : new LlmProviderException("All configured LLM providers failed");
    }

    private boolean isRetryableError(LlmProviderException e) {
        if (e == null || e.getMessage() == null) return false;
        String m = e.getMessage().toLowerCase();
        return m.contains("429") || m.contains("quota") || m.contains("rate") || m.contains("limit")
                || m.contains("500") || m.contains("502") || m.contains("503") || m.contains("504")
                || m.contains("timeout") || m.contains("connect") || m.contains("connection");
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
            
            StringBuilder fullPrompt = new StringBuilder();
            if (req.systemPrompt() != null && !req.systemPrompt().isBlank()) {
                fullPrompt.append("=== SYSTEM PROMPT ===\n").append(req.systemPrompt()).append("\n\n");
            }
            if (req.history() != null && !req.history().isEmpty()) {
                fullPrompt.append("=== CONVERSATION HISTORY ===\n");
                try {
                    fullPrompt.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(req.history()));
                } catch (Exception e) {
                    for (LlmMessageDTO m : req.history()) {
                        fullPrompt.append("[").append(m.role()).append("]: ").append(m.content()).append("\n");
                    }
                }
            } else if (req.userPrompt() != null) {
                fullPrompt.append(req.userPrompt());
            }

            LlmInteractionLogEntity log = new LlmInteractionLogEntity(
                req.taskId(), 
                req.phase() != null ? req.phase() : "Unspecified", 
                fullPrompt.toString(), 
                thought, 
                response
            );
            logRepository.save(log);
        }
    }

    private LlmProviderConfig resolveInitialConfig(LlmRequestDTO request) {
        Long requested = request.configId();
        if (requested == null) {
            return llmSettingsService.getDefaultConfig()
                    .orElseThrow(() -> new LlmProviderException("No default LLM provider configured"));
        } else {
            return llmSettingsService.getConfig(requested)
                    .orElseThrow(() -> new LlmProviderException("Unknown LLM provider ID: " + requested));
        }
    }

    private LlmProviderService createProviderService(LlmProviderConfig config) {
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
