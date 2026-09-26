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

    public LlmConfigController(LlmSettingsService llmSettingsService) {
        this.llmSettingsService = llmSettingsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLlmConfig() {
        return llmSettingsService.getDefaultConfig()
                .map(config -> ResponseEntity.ok(Map.of(
                        "activeProvider", config.getConfigName(),
                        "providerType", config.getProviderType(),
                        "activeModel", config.getActiveModel() != null ? config.getActiveModel() : "",
                        "baseUrl", config.getBaseUrl() != null ? config.getBaseUrl() : "",
                        "apiKeys", config.getApiKeys() != null ? config.getApiKeys() : List.of()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "activeProvider", "",
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
            String providerType = request.containsKey("providerType") ? (String) request.get("providerType") : "gemini";
            boolean isDefault = request.containsKey("isDefault") ? (Boolean) request.get("isDefault") : false;
            
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
                        apiKeys.add(new ApiKeyEntry(keyName, keyValue));
                    } else if (item instanceof String) { // Backward compatibility
                        apiKeys.add(new ApiKeyEntry("", (String) item));
                    }
                }
            }
            
            llmSettingsService.saveConfig(configName, providerType, model, baseUrl, apiKeys, isDefault);
        }
        return getLlmConfig();
    }
    
    @DeleteMapping("/{configName}")
    public ResponseEntity<Void> deleteProvider(@PathVariable String configName) {
        llmSettingsService.deleteConfig(configName);
        return ResponseEntity.ok().build();
    }
}
