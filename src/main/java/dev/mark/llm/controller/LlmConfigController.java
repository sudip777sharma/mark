package dev.mark.llm.controller;

import dev.mark.llm.config.LlmPropertiesConfig;
import dev.mark.llm.service.LlmRouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/config/llm")
public class LlmConfigController {

    private final LlmRouterService llmRouter;
    private final LlmPropertiesConfig geminiProperties;

    public LlmConfigController(LlmRouterService llmRouter, LlmPropertiesConfig geminiProperties) {
        this.llmRouter = llmRouter;
        this.geminiProperties = geminiProperties;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getLlmConfig() {
        String active = llmRouter.getActiveProvider();
        String activeModel = "gemini".equalsIgnoreCase(active)
                ? geminiProperties.model()
                : ("local".equalsIgnoreCase(active) ? "qwen2.5:7b-instruct-q4_K_M" : active);

        return ResponseEntity.ok(Map.of(
                "activeProvider", active,
                "activeModel", activeModel,
                "availableProviders", List.of("gemini", "local", "groq", "openrouter", "colab")
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> updateProvider(@RequestBody Map<String, String> request) {
        String provider = request.get("provider");
        if (provider != null && !provider.isBlank()) {
            llmRouter.setActiveProvider(provider);
        }
        return getLlmConfig();
    }
}
