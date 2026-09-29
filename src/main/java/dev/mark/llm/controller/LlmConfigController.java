package dev.mark.llm.controller;

import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.entity.ApiKeyEntry;
import dev.mark.llm.service.LlmSettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config/llm")
public class LlmConfigController {

    private final LlmSettingsService llmSettingsService;
    private final dev.mark.llm.service.LlmRouterService llmRouterService;

    public LlmConfigController(LlmSettingsService llmSettingsService, dev.mark.llm.service.LlmRouterService llmRouterService) {
        this.llmSettingsService = llmSettingsService;
        this.llmRouterService = llmRouterService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLlmConfig() {
        return llmSettingsService.getDefaultConfig()
                .map(config -> ResponseEntity.ok(Map.of(
                        "id", config.getId(),
                        "configName", config.getConfigName(),
                        "providerType", config.getProviderType(),
                        "activeModel", config.getActiveModel() != null ? config.getActiveModel() : "",
                        "baseUrl", config.getBaseUrl() != null ? config.getBaseUrl() : "",
                        "apiKeys", config.getApiKeys() != null ? config.getApiKeys() : List.of()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "id", -1L,
                        "configName", "",
                        "providerType", "gemini",
                        "activeModel", "",
                        "baseUrl", "",
                        "apiKeys", List.of()
                )));
    }

    @GetMapping("/all")
    public ResponseEntity<List<LlmProviderConfig>> getAllConfigs() {
        return ResponseEntity.ok(llmSettingsService.getAllConfigs());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateProvider(@RequestBody Map<String, Object> request) {
        String configName = (String) request.get("configName");
        if (configName != null && !configName.isBlank()) {
            Long id = null;
            if (request.get("id") != null) {
                if (request.get("id") instanceof Number) {
                    id = ((Number) request.get("id")).longValue();
                } else if (request.get("id") instanceof String) {
                    try { id = Long.parseLong((String) request.get("id")); } catch (Exception e) {}
                }
            }

            String providerType = request.containsKey("providerType") ? (String) request.get("providerType") : "gemini";
            boolean isDefault = request.containsKey("isDefault") ? (Boolean) request.get("isDefault") : false;
            boolean isConfigured = request.containsKey("isConfigured") ? (Boolean) request.get("isConfigured") : false;
            
            String model = (String) request.get("model");
            String baseUrl = (String) request.get("baseUrl");
            
            List<ApiKeyEntry> apiKeys = new ArrayList<>();
            Object rawApiKeys = request.get("apiKeys");
            if (rawApiKeys instanceof List) {
                for (Object item : (List<?>) rawApiKeys) {
                    if (item instanceof Map) {
                        Map<?, ?> map = (Map<?, ?>) item;
                        String keyName = map.get("keyName") != null ? map.get("keyName").toString() : "";
                        String keyValue = map.get("keyValue") != null ? map.get("keyValue").toString() : "";
                        Boolean isActive = true;
                        if (map.get("isActive") != null) {
                            isActive = Boolean.valueOf(map.get("isActive").toString());
                        }
                        apiKeys.add(new ApiKeyEntry(keyName, keyValue, isActive));
                    } else if (item instanceof String) { // Backward compatibility
                        apiKeys.add(new ApiKeyEntry("", (String) item, true));
                    }
                }
            }
            
            llmSettingsService.saveConfig(id, configName, providerType, model, baseUrl, apiKeys, isDefault, isConfigured);
        }
        return getLlmConfig();
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProvider(@PathVariable Long id) {
        llmSettingsService.deleteConfig(id);
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{id}/test")
    public ResponseEntity<Map<String, Object>> testProvider(@PathVariable Long id) {
        try {
            dev.mark.llm.dto.LlmRequestDTO request = new dev.mark.llm.dto.LlmRequestDTO(
                id, "You are a network tester.", "Reply exactly with 'OK'", java.util.List.of(), java.util.List.of(), null
            );
            dev.mark.llm.dto.LlmResponseDTO response = llmRouterService.complete(request);
            if (response != null && response.content() != null && !response.content().isBlank()) {
                // Mark as configured
                llmSettingsService.getConfig(id).ifPresent(config -> {
                    llmSettingsService.saveConfig(
                        config.getId(), config.getConfigName(), config.getProviderType(),
                        config.getActiveModel(), config.getBaseUrl(), config.getApiKeys(),
                        config.isDefault(), true
                    );
                });
                return ResponseEntity.ok(Map.of("status", "success", "message", "Connection successful!"));
            }
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Empty response received."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    @PostMapping("/test-key")
    public ResponseEntity<Map<String, Object>> testSpecificKey(@RequestBody Map<String, Object> request) {
        try {
            String providerType = (String) request.get("providerType");
            String apiKey = (String) request.get("apiKey");
            String model = (String) request.get("model");
            String baseUrl = (String) request.get("baseUrl");
            
            if (apiKey == null || apiKey.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "API Key is required."));
            }
            if (model == null || model.isBlank()) {
                if ("gemini".equalsIgnoreCase(providerType)) model = "gemini-1.5-flash";
                else model = "default";
            }
            
            org.springframework.web.client.RestClient client = org.springframework.web.client.RestClient.create();
            
            if ("gemini".equalsIgnoreCase(providerType)) {
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Ping\"}]}]}";
                
                String res = client.post().uri(url)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Connection successful!"));
            } else {
                if (baseUrl == null || baseUrl.isBlank()) {
                    if ("groq".equalsIgnoreCase(providerType)) baseUrl = "https://api.groq.com/openai/v1";
                    else if ("openrouter".equalsIgnoreCase(providerType)) baseUrl = "https://openrouter.ai/api/v1";
                    else baseUrl = "https://api.openai.com/v1";
                }
                
                String url = baseUrl;
                if (!url.endsWith("/")) url += "/";
                url += "chat/completions";
                
                String body = "{\"model\":\"" + model + "\", \"messages\":[{\"role\":\"user\", \"content\":\"Ping\"}]}";
                
                String res = client.post().uri(url)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
                return ResponseEntity.ok(Map.of("status", "success", "message", "Connection successful!"));
            }
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Failed: " + e.getStatusCode().value() + " " + errorBody));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
