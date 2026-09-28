package dev.mark.llm.service;



import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

/**
 * - What the class does: Implements the LlmProviderService interface to integrate Google's Gemini LLM into the application.
 * - Why it is useful: Abstracts the Google GenAI SDK, enabling seamless text generation, function calling, and structured task planning.
 * - How it fits in the flow of the application: Acts as a concrete provider adapter invoked by upper-layer services to fulfill AI and planning requests.
 * - Its methods and variables and how they are useful:
 *   - client and properties: Store the Google GenAI client and configuration settings for API communication.
 *   - complete(): Processes standard chat requests, tool definitions, and conversation history to return text or tool calls.
 *   - plan(): Enforces a JSON response schema to generate sequential task lists.
 *   - mapMessage() and mapSchema(): Translate application-specific DTOs into Google GenAI SDK types.
 * - Its logic and how it gets fit into the overall application logic: Converts incoming generic DTO requests into Gemini-specific structures, executes remote API calls, handles responses or exceptions, and maps them back to standardized application DTOs.
 */

@Component
public class GeminiLlmProviderService implements LlmProviderService {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmProviderService.class);
    private final LlmSettingsService llmSettingsService;

    public GeminiLlmProviderService(LlmSettingsService llmSettingsService) {
        this.llmSettingsService = llmSettingsService;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public LlmResponseDTO complete(LlmRequestDTO request) {
        Long configId = request.configId();
        if (configId == null) {
            configId = llmSettingsService.getDefaultConfig().map(c -> c.getId()).orElse(null);
        }
        
        Long finalConfigId = configId;
        String apiKey = llmSettingsService.getNextApiKey(finalConfigId);
        String model = llmSettingsService.getConfig(finalConfigId).map(c -> c.getActiveModel()).orElse("gemini-1.5-flash");
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmProviderException("Gemini API key is not configured for: " + finalConfigId);
        }
        
        Client client = Client.builder().apiKey(apiKey).build();

        try {
            List<Content> contents = new ArrayList<>();

            // Add current user prompt if not empty (this should come before history)
            if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
                contents.add(Content.builder().role("user").parts(List.of(Part.builder().text(request.userPrompt()).build())).build());
            }

            // Map history
            List<Part> pendingToolParts = new ArrayList<>();
            for (LlmMessageDTO msg : request.history()) {
                if ("tool".equals(msg.role())) {
                    pendingToolParts.addAll(mapMessage(msg).parts().orElse(List.of()));
                } else {
                    if (!pendingToolParts.isEmpty()) {
                        contents.add(Content.builder().role("user").parts(pendingToolParts).build());
                        pendingToolParts = new ArrayList<>();
                    }
                    contents.add(mapMessage(msg));
                }
            }
            if (!pendingToolParts.isEmpty()) {
                contents.add(Content.builder().role("user").parts(pendingToolParts).build());
            }

            // Map Tools
            List<FunctionDeclaration> declarations = new ArrayList<>();
            for (LlmToolDefinitionDTO def : request.tools()) {
                declarations.add(
                    FunctionDeclaration.builder()
                        .name(def.name())
                        .description(def.description())
                        .parameters(mapSchema(def.parameters()))
                        .build()
                );
            }

            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .thinkingConfig(com.google.genai.types.ThinkingConfig.builder().includeThoughts(false).build());

            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                configBuilder.systemInstruction(Content.builder().parts(List.of(Part.builder().text(request.systemPrompt()).build())).build());
            }

            if (!declarations.isEmpty()) {
                configBuilder.tools(List.of(Tool.builder().functionDeclarations(declarations).build()));
            }

            long startTime = System.currentTimeMillis();
            String promptSnippet = request.userPrompt() != null ? request.userPrompt() : (request.history().isEmpty() ? "empty" : "history-context");
            log.info(">>> [GEMINI:REQ] model={} toolsCount={} historySize={} prompt='{}'",
                model, declarations.size(), request.history().size(), promptSnippet);

            GenerateContentResponse response = client.models.generateContent(
                model,
                contents,
                configBuilder.build()
            );

            long duration = System.currentTimeMillis() - startTime;

            if (response.candidates().isEmpty() || response.candidates().get().isEmpty()) {
                log.warn("!!! [GEMINI:EMPTY] model={} durationMs={} returned zero candidates", model, duration);
                throw new LlmProviderException("Gemini returned no candidates");
            }

            Content bestContent = response.candidates().get().get(0).content().orElse(null);
            if (bestContent == null || bestContent.parts().isEmpty() || bestContent.parts().get().isEmpty()) {
                log.info("<<< [GEMINI:RES] model={} durationMs={} bestContent is empty", model, duration);
                return new LlmResponseDTO("", name(), false);
            }

            StringBuilder textContent = new StringBuilder();
            List<LlmToolCallDTO> toolCalls = new ArrayList<>();

            for (Part part : bestContent.parts().get()) {
                if (part.text().isPresent()) {
                    textContent.append(part.text().get());
                }
                if (part.functionCall().isPresent()) {
                    FunctionCall fc = part.functionCall().get();
                    String id = fc.id().orElse(java.util.UUID.randomUUID().toString());
                    Map<String, Object> metadata = new java.util.HashMap<>();
                    if (part.thoughtSignature().isPresent()) {
                        metadata.put("gemini.thoughtSignature", part.thoughtSignature().get());
                    }
                    toolCalls.add(new LlmToolCallDTO(id, fc.name().orElse(""), fc.args().orElse(Map.of()), metadata));
                }
            }

            log.info("<<< [GEMINI:RES] model={} durationMs={} textChars={} toolCallsCount={}",
                model, duration, textContent.length(), toolCalls.size());
            for (LlmToolCallDTO tc : toolCalls) {
                log.info("    -> [GEMINI:TOOL_CALL] tool={} args={}", tc.name(), tc.arguments());
            }

            return new LlmResponseDTO(textContent.toString(), name(), false, toolCalls);

        } catch (Exception e) {
            log.error("!!! [GEMINI:FAILED] model={} error={}", model, e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("429") || e.getMessage().toLowerCase().contains("quota"))) {
                if (e.getMessage().toLowerCase().contains("perday") || e.getMessage().toLowerCase().contains("daily")) {
                    log.warn("!!! [GEMINI:DAILY_QUOTA] Marking API key as inactive due to daily limit.");
                    llmSettingsService.markKeyAsInactive(finalConfigId, apiKey);
                }
            }
            throw new LlmProviderException("Gemini request failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    @Override
    public PlanResponseDTO plan(LlmRequestDTO request) {
        Long configId = request.configId();
        if (configId == null) {
            configId = llmSettingsService.getDefaultConfig().map(c -> c.getId()).orElse(null);
        }
        
        Long finalConfigId = configId;
        String apiKey = llmSettingsService.getNextApiKey(finalConfigId);
        String model = llmSettingsService.getConfig(finalConfigId).map(c -> c.getActiveModel()).orElse("gemini-1.5-flash");
        
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlmProviderException("Gemini API key is not configured for: " + finalConfigId);
        }
        
        Client client = Client.builder().apiKey(apiKey).build();

        try {
            List<Content> contents = new ArrayList<>();
            if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
                contents.add(Content.builder().role("user").parts(List.of(Part.builder().text(request.userPrompt()).build())).build());
            }

            List<Part> pendingToolParts = new ArrayList<>();
            for (LlmMessageDTO msg : request.history()) {
                if ("tool".equals(msg.role())) {
                    pendingToolParts.addAll(mapMessage(msg).parts().orElse(List.of()));
                } else {
                    if (!pendingToolParts.isEmpty()) {
                        contents.add(Content.builder().role("user").parts(pendingToolParts).build());
                        pendingToolParts = new ArrayList<>();
                    }
                    contents.add(mapMessage(msg));
                }
            }
            if (!pendingToolParts.isEmpty()) {
                contents.add(Content.builder().role("user").parts(pendingToolParts).build());
            }

            Schema stringSchema = Schema.builder().type("STRING").build();
            Schema arraySchema = Schema.builder().type("ARRAY").items(stringSchema).description("List of sequential tasks").build();
            Schema objectSchema = Schema.builder().type("OBJECT").properties(Map.of("steps", arraySchema)).build();

            GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .responseMimeType("application/json")
                .responseSchema(objectSchema)
                .thinkingConfig(com.google.genai.types.ThinkingConfig.builder().includeThoughts(false).build());

            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                configBuilder.systemInstruction(Content.builder().parts(List.of(Part.builder().text(request.systemPrompt()).build())).build());
            }

            long startTime = System.currentTimeMillis();
            String goalSnippet = request.userPrompt() != null ? request.userPrompt() : "empty-goal";
            log.info(">>> [GEMINI:PLAN:REQ] model={} goal='{}'", model, goalSnippet);

            GenerateContentResponse response = client.models.generateContent(
                model,
                contents,
                configBuilder.build()
            );

            long duration = System.currentTimeMillis() - startTime;

            if (response.candidates().isEmpty() || response.candidates().get().isEmpty()) {
                log.warn("!!! [GEMINI:PLAN:EMPTY] model={} durationMs={} returned zero candidates", model, duration);
                throw new LlmProviderException("Gemini returned no candidates");
            }

            Content bestContent = response.candidates().get().get(0).content().orElse(null);
            if (bestContent == null || bestContent.parts().isEmpty() || bestContent.parts().get().isEmpty()) {
                log.info("<<< [GEMINI:PLAN:RES] model={} durationMs={} bestContent is empty", model, duration);
                return new PlanResponseDTO(List.of());
            }

            String json = bestContent.parts().get().get(0).text().orElse("{}");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);
            JsonNode stepsNode = root.path("steps");
            List<String> steps = new ArrayList<>();
            if (stepsNode.isArray()) {
                for (JsonNode n : stepsNode) {
                    steps.add(n.asText());
                }
            }
            log.info("<<< [GEMINI:PLAN:RES] model={} durationMs={} stepsCount={}", model, duration, steps.size());
            for (int i = 0; i < steps.size(); i++) {
                log.info("    Step {}: {}", i + 1, steps.get(i));
            }
            return new PlanResponseDTO(steps);

        } catch (Exception e) {
            log.error("!!! [GEMINI:PLAN:FAILED] model={} error={}", model, e.getMessage(), e);
            if (e.getMessage() != null && (e.getMessage().toLowerCase().contains("429") || e.getMessage().toLowerCase().contains("quota"))) {
                if (e.getMessage().toLowerCase().contains("perday") || e.getMessage().toLowerCase().contains("daily")) {
                    log.warn("!!! [GEMINI:PLAN:DAILY_QUOTA] Marking API key as inactive due to daily limit.");
                    llmSettingsService.markKeyAsInactive(finalConfigId, apiKey);
                }
            }
            throw new LlmProviderException("Gemini planning request failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    private Content mapMessage(LlmMessageDTO msg) {
        if ("user".equals(msg.role()) || "system".equals(msg.role())) {
            List<Part> parts = new ArrayList<>();
            if (msg.content() != null && !msg.content().isBlank()) {
                parts.add(Part.builder().text(msg.content()).build());
            }
            if (msg.base64Image() != null && msg.mimeType() != null) {
                byte[] decoded = java.util.Base64.getDecoder().decode(msg.base64Image());
                parts.add(Part.builder().inlineData(
                    com.google.genai.types.Blob.builder().data(decoded).mimeType(msg.mimeType()).build()
                ).build());
            }
            return Content.builder().role(msg.role()).parts(parts).build();
        } else if ("assistant".equals(msg.role())) {
            List<Part> parts = new ArrayList<>();
            if (msg.content() != null && !msg.content().isBlank()) {
                parts.add(Part.builder().text(msg.content()).build());
            }
            for (LlmToolCallDTO toolCall : msg.toolCalls()) {
                Part.Builder partBuilder = Part.builder()
                        .functionCall(FunctionCall.builder()
                                .id(toolCall.id())
                                .name(toolCall.name())
                                .args(toolCall.arguments())
                                .build());
                if (toolCall.metadata().containsKey("gemini.thoughtSignature")) {
                    partBuilder.thoughtSignature((byte[]) toolCall.metadata().get("gemini.thoughtSignature"));
                }
                parts.add(partBuilder.build());
            }
            return Content.builder().role("model").parts(parts).build();
        } else if ("tool".equals(msg.role())) {
            LlmToolObservationDTO obs = msg.toolObservation();
            Map<String, Object> respMap = Map.of("result", obs.observation(), "error", !obs.successful());
            return Content.builder().role("user").parts(List.of(
                Part.builder().functionResponse(
                    FunctionResponse.builder().id(obs.toolCallId()).name(obs.toolName()).response(respMap).build()
                ).build()
            )).build();
        }
        return Content.builder().role("user").parts(List.of(Part.builder().text(msg.content()).build())).build();
    }

    @SuppressWarnings("unchecked")
    private Schema mapSchema(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return Schema.builder().type("OBJECT").build();
        }
        Schema.Builder builder = Schema.builder();
        String type = (String) map.get("type");
        if (type != null) {
            builder.type(type.toUpperCase());
        } else {
            builder.type("OBJECT");
        }

        if (map.containsKey("description")) {
            builder.description((String) map.get("description"));
        }

        if (map.containsKey("properties")) {
            Map<String, Object> props = (Map<String, Object>) map.get("properties");
            java.util.Map<String, Schema> mappedProps = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : props.entrySet()) {
                mappedProps.put(entry.getKey(), mapSchema((Map<String, Object>) entry.getValue()));
            }
            builder.properties(mappedProps);
        }

        if (map.containsKey("items")) {
            builder.items(mapSchema((Map<String, Object>) map.get("items")));
        }

        if (map.containsKey("required")) {
            builder.required((List<String>) map.get("required"));
        }

        return builder.build();
    }
}

