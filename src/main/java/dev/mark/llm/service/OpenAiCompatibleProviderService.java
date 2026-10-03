package dev.mark.llm.service;

import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * - Implements the LlmProviderService interface to handle integrations with LLM providers using the OpenAI API specification.
 * - Useful for standardizing chat completions, tool execution, and structured JSON planning across various interchangeable LLM backends.
 * - Acts as the concrete service layer bridging core application logic and external HTTP LLM endpoints.
 * - Fields: name, model, client, and objectMapper maintain provider identity, configuration, HTTP communication, and JSON serialization.
 * - Integrates by serializing internal DTOs into OpenAI-compliant JSON maps, executing synchronous HTTP POST requests via RestClient, and mapping responses back into standard application DTOs.
 */
public class OpenAiCompatibleProviderService implements LlmProviderService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProviderService.class);
    private final String name;
    private final String model;
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final LlmSettingsService llmSettingsService;
    private final Long configId;
    private final String apiKey;

    public OpenAiCompatibleProviderService(Long configId, String name, String baseUrl, String apiKey, String model, RestClient.Builder builder, ObjectMapper objectMapper, LlmSettingsService llmSettingsService) {
        this.configId = configId;
        this.name = name;
        this.model = model;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.llmSettingsService = llmSettingsService;
        RestClient.Builder clientBuilder = builder.baseUrl(baseUrl);
        if (StringUtils.hasText(apiKey)) {
            clientBuilder.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.client = clientBuilder.build();
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public LlmResponseDTO complete(LlmRequestDTO request) {
        return completeStream(request, null);
    }

    @Override
    public LlmResponseDTO completeStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);

            if (onChunk != null) {
                requestBody.put("stream", true);
            }

            log.info(">>> [LLM COMPLETION REQUEST - {}]", name);
            log.info(">>> User Prompt: {}", request.userPrompt());

            if (onChunk == null) {
                String response = client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);

                LlmResponseDTO parsedResponse = parseResponse(response);
                log.info("<<< [LLM COMPLETION RESPONSE - {}]", name);
                log.info("<<< Content Length: {}, Tool Calls: {}",
                         parsedResponse.content() == null ? 0 : parsedResponse.content().length(),
                         parsedResponse.toolCalls().size());
                return parsedResponse;
            } else {
                return client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((clientRequest, clientResponse) -> {
                            if (clientResponse.getStatusCode().isError()) {
                                String errBody = new String(clientResponse.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                int status = clientResponse.getStatusCode().value();
                                if (status == 429 || status == 402) {
                                    String msg = errBody.toLowerCase();
                                    if (msg.contains("free tier limit") || msg.contains("per day") || msg.contains("daily") || msg.contains("quota") || msg.contains("insufficient_quota") || msg.contains("insufficient balance")) {
                                        log.warn("!!! [OPENAI_COMPATIBLE:DAILY_QUOTA] Exhausted limit for provider '{}'. Message: {}. Deactivating key...", name, msg);
                                        if (configId != null && apiKey != null) {
                                            llmSettingsService.markKeyAsInactive(configId, apiKey);
                                        }
                                    }
                                }
                                throw new LlmProviderException(name + " API completion request failed with status " + status + ": " + errBody);
                            }
                            StringBuilder sbContent = new StringBuilder();
                            List<LlmToolCallDTO> parsedToolCalls = new ArrayList<>();
                            Map<Integer, Map<String, Object>> toolCallBuilders = new LinkedHashMap<>();
                            
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(clientResponse.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6).trim();
                                        if ("[DONE]".equals(data)) break;
                                        try {
                                            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(data);
                                            com.fasterxml.jackson.databind.JsonNode deltaNode = node.path("choices").path(0).path("delta");
                                            
                                            // 1. Accumulate Content
                                            com.fasterxml.jackson.databind.JsonNode contentNode = deltaNode.path("content");
                                            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                                String chunk = contentNode.asText();
                                                sbContent.append(chunk);
                                                onChunk.accept(chunk);
                                            }
                                            
                                            // 2. Accumulate Tool Calls
                                            com.fasterxml.jackson.databind.JsonNode toolCallsNode = deltaNode.path("tool_calls");
                                            if (toolCallsNode.isArray()) {
                                                for (com.fasterxml.jackson.databind.JsonNode tcNode : toolCallsNode) {
                                                    int index = tcNode.path("index").asInt();
                                                    Map<String, Object> builder = toolCallBuilders.computeIfAbsent(index, k -> new LinkedHashMap<>());
                                                    
                                                    if (tcNode.has("id")) builder.put("id", tcNode.get("id").asText());
                                                    
                                                    com.fasterxml.jackson.databind.JsonNode functionNode = tcNode.path("function");
                                                    if (!functionNode.isMissingNode()) {
                                                        if (functionNode.has("name")) builder.put("name", functionNode.get("name").asText());
                                                        if (functionNode.has("arguments")) {
                                                            String existingArgs = (String) builder.getOrDefault("arguments", "");
                                                            builder.put("arguments", existingArgs + functionNode.get("arguments").asText());
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (Exception ex) {
                                            log.warn("Failed to parse SSE completion chunk: " + data, ex);
                                        }
                                    }
                                }
                            }
                            
                            for (Map<String, Object> builder : toolCallBuilders.values()) {
                                String id = (String) builder.get("id");
                                String functionName = (String) builder.get("name");
                                String argsJson = (String) builder.getOrDefault("arguments", "{}");
                                Map<String, Object> args = Map.of();
                                try {
                                    if (!argsJson.trim().isEmpty()) {
                                        args = objectMapper.readValue(argsJson, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                                    }
                                } catch (Exception ex) {
                                    log.warn("Failed to parse tool call args: {}", argsJson, ex);
                                }
                                parsedToolCalls.add(new LlmToolCallDTO(id != null ? id : java.util.UUID.randomUUID().toString(), functionName != null ? functionName : "", args, Map.of()));
                            }

                            log.info("<<< [LLM COMPLETION RESPONSE - {}]", name);
                            log.info("<<< Content Length: {}, Tool Calls: {}", sbContent.length(), parsedToolCalls.size());
                            
                            return new LlmResponseDTO(sbContent.toString(), name, false, parsedToolCalls);
                        });
            }

        } catch (RestClientException exception) {
            throw new LlmProviderException(name + " API request failed (Connection error)", exception);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PlanResponseDTO plan(LlmRequestDTO request) {
        return planStream(request, null);
    }

    @Override
    public PlanResponseDTO planStream(LlmRequestDTO request, java.util.function.Consumer<String> onChunk) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);
            requestBody.put("response_format", Map.of("type", "json_object"));
            if (onChunk != null) {
                requestBody.put("stream", true);
            }

            List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
            boolean hasSystem = false;
            for (Map<String, Object> msg : messages) {
                if ("system".equals(msg.get("role"))) {
                    Map<String, Object> newMsg = new LinkedHashMap<>(msg);
                    newMsg.put("content", newMsg.get("content") + "\n\nIMPORTANT: You must return a valid JSON object with a single key 'steps' that contains a JSON array of strings.");
                    messages.set(messages.indexOf(msg), newMsg);
                    hasSystem = true;
                    break;
                }
            }
            if (!hasSystem) {
                messages.add(0, Map.of("role", "system", "content", "You must return a valid JSON object with a single key 'steps' that contains a JSON array of strings."));
            }

            log.info(">>> [LLM PLAN REQUEST - {}]", name);
            log.info(">>> User Prompt: {}", request.userPrompt());

            String fullContent;

            if (onChunk == null) {
                String response = client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(String.class);
                LlmResponseDTO llmResponse = parseResponse(response);
                fullContent = llmResponse.content();
            } else {
                fullContent = client.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .exchange((clientRequest, clientResponse) -> {
                            if (clientResponse.getStatusCode().isError()) {
                                String errBody = new String(clientResponse.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                                int status = clientResponse.getStatusCode().value();
                                if (status == 429 || status == 402) {
                                    String msg = errBody.toLowerCase();
                                    if (msg.contains("free tier limit") || msg.contains("per day") || msg.contains("daily") || msg.contains("quota") || msg.contains("insufficient_quota") || msg.contains("insufficient balance")) {
                                        log.warn("!!! [OPENAI_COMPATIBLE:DAILY_QUOTA] Exhausted limit for provider '{}'. Message: {}. Deactivating key...", name, msg);
                                        if (configId != null && apiKey != null) {
                                            llmSettingsService.markKeyAsInactive(configId, apiKey);
                                        }
                                    }
                                }
                                throw new LlmProviderException(name + " API plan request failed with status " + status + ": " + errBody);
                            }
                            StringBuilder sb = new StringBuilder();
                            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(clientResponse.getBody(), java.nio.charset.StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.startsWith("data: ")) {
                                        String data = line.substring(6).trim();
                                        if ("[DONE]".equals(data)) break;
                                        try {
                                            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(data);
                                            com.fasterxml.jackson.databind.JsonNode contentNode = node.path("choices").path(0).path("delta").path("content");
                                            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                                                String chunk = contentNode.asText();
                                                sb.append(chunk);
                                                onChunk.accept(chunk);
                                            }
                                        } catch (Exception e) {
                                            // ignore parse errors for incomplete chunks
                                        }
                                    }
                                }
                            }
                            return sb.toString();
                        });
            }

            log.info("<<< [LLM PLAN RESPONSE - {}]", name);
            log.info("<<< Raw Content:\n{}", fullContent);
            String content = fullContent.trim();

            if (content.startsWith("```json")) {
                content = content.substring(7);
                if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3);
                if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
            }

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(content);
            com.fasterxml.jackson.databind.JsonNode stepsNode = root.path("steps");
            List<String> steps = new ArrayList<>();
            if (stepsNode.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode n : stepsNode) {
                    steps.add(n.asText());
                }
            }
            return new PlanResponseDTO(steps);

        } catch (org.springframework.web.client.RestClientResponseException exception) {
            handleRateLimit(exception);
            throw new LlmProviderException(name + " API plan request failed with status " + exception.getStatusCode() + ": " + exception.getResponseBodyAsString(), exception);
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new LlmProviderException(name + " API plan request failed (Connection error)", exception);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new LlmProviderException(name + " returned malformed JSON during planning", exception);
        }
    }

    private void handleRateLimit(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429 || status == 402) {
            String msg = exception.getResponseBodyAsString().toLowerCase();
            if (msg.contains("free tier limit") || msg.contains("per day") || msg.contains("daily") || msg.contains("quota") || msg.contains("insufficient_quota") || msg.contains("insufficient balance")) {
                log.warn("!!! [OPENAI_COMPATIBLE:DAILY_QUOTA] Exhausted limit for provider '{}'. Message: {}. Deactivating key...", name, msg);
                if (configId != null && apiKey != null) {
                    llmSettingsService.markKeyAsInactive(configId, apiKey);
                }
            }
        }
    }

    private Map<String, Object> buildRequestBody(LlmRequestDTO request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        if (StringUtils.hasText(request.userPrompt())) {
            messages.add(Map.of("role", "user", "content", request.userPrompt()));
        }
        messages.addAll(request.history().stream().map(this::toOpenAiMessage).toList());
        body.put("messages", messages);

        if (!request.tools().isEmpty()) {
            body.put("tools", request.tools().stream().map(this::toOpenAiTool).toList());
        }
        if (request.toolChoice() != null) {
            body.put("tool_choice", request.toolChoice());
        }
        return body;
    }

    private LlmResponseDTO parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new LlmProviderException(name + " returned an empty response");
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode message = root.path("choices").path(0).path("message");
            if (message.isMissingNode() || !message.isObject()) {
                throw new LlmProviderException(name + " response has no choices[0].message");
            }
            String content = message.path("content").isNull() || message.path("content").isMissingNode()
                    ? "" : message.path("content").asText();
            return new LlmResponseDTO(content, name(), false, parseToolCalls(message.path("tool_calls")));
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException(name + " returned malformed JSON", exception);
        }
    }

    private Map<String, Object> toOpenAiTool(LlmToolDefinitionDTO tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());
        return Map.of("type", "function", "function", function);
    }

    private Map<String, Object> toOpenAiMessage(LlmMessageDTO message) {
        Map<String, Object> serialized = new LinkedHashMap<>();
        serialized.put("role", message.role());

        if (message.base64Image() != null && message.mimeType() != null) {
            List<Map<String, Object>> contentList = new ArrayList<>();
            if (StringUtils.hasText(message.content())) {
                contentList.add(Map.of("type", "text", "text", message.content()));
            }
            contentList.add(Map.of(
                "type", "image_url",
                "image_url", Map.of("url", "data:" + message.mimeType() + ";base64," + message.base64Image())
            ));
            serialized.put("content", contentList);
        } else {
            serialized.put("content", message.toolObservation() == null ? message.content() : serializeObservation(message.toolObservation()));
        }

        if (message.toolCallId() != null) serialized.put("tool_call_id", message.toolCallId());
        if (!message.toolCalls().isEmpty()) {
            serialized.put("tool_calls", message.toolCalls().stream().map(this::toOpenAiToolCall).toList());
        }
        return serialized;
    }

    private Map<String, Object> toOpenAiToolCall(LlmToolCallDTO toolCall) {
        try {
            return Map.of("id", toolCall.id(), "type", "function", "function", Map.of(
                    "name", toolCall.name(), "arguments", objectMapper.writeValueAsString(toolCall.arguments())));
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException("Could not serialize tool call history", exception);
        }
    }

    private String serializeObservation(LlmToolObservationDTO observation) {
        try {
            return objectMapper.writeValueAsString(observation.asMap());
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException("Could not serialize tool observation history", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<LlmToolCallDTO> parseToolCalls(JsonNode toolCalls) throws JsonProcessingException {
        if (toolCalls.isMissingNode() || toolCalls.isNull()) return List.of();
        if (!toolCalls.isArray()) throw new LlmProviderException(name + " response has invalid tool_calls");
        List<LlmToolCallDTO> parsed = new ArrayList<>();
        for (JsonNode toolCall : toolCalls) {
            JsonNode function = toolCall.path("function");
            if (!function.isObject() || !function.path("name").isTextual() || !function.path("arguments").isTextual()) {
                throw new LlmProviderException(name + " response has malformed tool call");
            }
            JsonNode arguments = objectMapper.readTree(function.path("arguments").asText());
            if (!arguments.isObject()) throw new LlmProviderException("Tool call arguments must be a JSON object");
            parsed.add(new LlmToolCallDTO(toolCall.path("id").asText(""), function.path("name").asText(), objectMapper.convertValue(arguments, Map.class)));
        }
        return List.copyOf(parsed);
    }
}
