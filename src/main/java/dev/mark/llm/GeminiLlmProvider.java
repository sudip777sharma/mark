package dev.mark.llm;

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

@Component
public class GeminiLlmProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiLlmProvider.class);
    private final Client client;
    private final GeminiLlmProperties properties;

    public GeminiLlmProvider(GeminiLlmProperties properties) {
        this.properties = properties;
        // If API key is empty/null, Client constructor might fail or throw, but that's expected if not configured.
        this.client = Client.builder().apiKey(properties.apiKey()).build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        try {
            List<Content> contents = new ArrayList<>();
            
            // Add current user prompt if not empty (this should come before history)
            if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
                contents.add(Content.builder().role("user").parts(List.of(Part.builder().text(request.userPrompt()).build())).build());
            }

            // Map history
            for (LlmMessage msg : request.history()) {
                contents.add(mapMessage(msg));
            }

            // Map Tools
            List<FunctionDeclaration> declarations = new ArrayList<>();
            for (LlmToolDefinition def : request.tools()) {
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

            GenerateContentResponse response = client.models.generateContent(
                properties.model(),
                contents,
                configBuilder.build()
            );

            if (response.candidates().isEmpty() || response.candidates().get().isEmpty()) {
                throw new LlmProviderException("Gemini returned no candidates");
            }

            Content bestContent = response.candidates().get().get(0).content().orElse(null);
            if (bestContent == null || bestContent.parts().isEmpty() || bestContent.parts().get().isEmpty()) {
                return new LlmResponse("", name(), false);
            }

            StringBuilder textContent = new StringBuilder();
            List<LlmToolCall> toolCalls = new ArrayList<>();

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
                    toolCalls.add(new LlmToolCall(id, fc.name().orElse(""), fc.args().orElse(Map.of()), metadata));
                }
            }

            return new LlmResponse(textContent.toString(), name(), false, toolCalls);

        } catch (Exception e) {
            log.error("Gemini request failed: " + e.getMessage(), e);
            throw new LlmProviderException("Gemini request failed", e);
        }
    }

    @Override
    public PlanResponse plan(LlmRequest request) {
        try {
            List<Content> contents = new ArrayList<>();
            if (request.userPrompt() != null && !request.userPrompt().isBlank()) {
                contents.add(Content.builder().role("user").parts(List.of(Part.builder().text(request.userPrompt()).build())).build());
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

            GenerateContentResponse response = client.models.generateContent(
                properties.model(),
                contents,
                configBuilder.build()
            );

            if (response.candidates().isEmpty() || response.candidates().get().isEmpty()) {
                throw new LlmProviderException("Gemini returned no candidates");
            }

            Content bestContent = response.candidates().get().get(0).content().orElse(null);
            if (bestContent == null || bestContent.parts().isEmpty() || bestContent.parts().get().isEmpty()) {
                return new PlanResponse(List.of());
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
            return new PlanResponse(steps);

        } catch (Exception e) {
            log.error("Gemini planning request failed: " + e.getMessage(), e);
            throw new LlmProviderException("Gemini planning request failed", e);
        }
    }

    private Content mapMessage(LlmMessage msg) {
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
            for (LlmToolCall toolCall : msg.toolCalls()) {
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
            LlmToolObservation obs = msg.toolObservation();
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
