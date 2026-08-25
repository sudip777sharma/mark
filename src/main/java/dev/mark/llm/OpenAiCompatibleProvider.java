package dev.mark.llm;

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

public class OpenAiCompatibleProvider implements LlmProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);
    private final String name;
    private final String model;
    private final RestClient client;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProvider(String name, String baseUrl, String apiKey, String model, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.name = name;
        this.model = model;
        this.objectMapper = objectMapper;
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
    public LlmResponse complete(LlmRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);
            if (log.isDebugEnabled()) {
                try { log.debug("event=llm_request provider={} body={}", name, objectMapper.writeValueAsString(requestBody)); }
                catch (JsonProcessingException ignored) { }
            }
            
            String response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
                    
            log.debug("event=llm_response provider={} body={}", name, response);
            return parseResponse(response);
            
        } catch (RestClientResponseException exception) {
            throw new LlmProviderException(name + " API request failed with status " + exception.getStatusCode() + ": " + exception.getResponseBodyAsString(), exception);
        } catch (RestClientException exception) {
            throw new LlmProviderException(name + " API request failed (Connection error)", exception);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public PlanResponse plan(LlmRequest request) {
        try {
            Map<String, Object> requestBody = buildRequestBody(request);
            requestBody.put("response_format", Map.of("type", "json_object"));
            
            List<Map<String, Object>> messages = (List<Map<String, Object>>) requestBody.get("messages");
            boolean hasSystem = false;
            for (Map<String, Object> msg : messages) {
                if ("system".equals(msg.get("role"))) {
                    // Update the system message map. We must create a new map since Map.of creates immutable maps.
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

            if (log.isDebugEnabled()) {
                try { log.debug("event=llm_request_plan provider={} body={}", name, objectMapper.writeValueAsString(requestBody)); }
                catch (JsonProcessingException ignored) { }
            }
            
            String response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
                    
            log.debug("event=llm_response_plan provider={} body={}", name, response);
            
            LlmResponse llmResponse = parseResponse(response);
            String content = llmResponse.content().trim();
            
            if (content.startsWith("```json")) {
                content = content.substring(7);
                if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
            } else if (content.startsWith("```")) {
                content = content.substring(3);
                if (content.endsWith("```")) content = content.substring(0, content.length() - 3);
            }
            
            JsonNode root = objectMapper.readTree(content);
            JsonNode stepsNode = root.path("steps");
            List<String> steps = new ArrayList<>();
            if (stepsNode.isArray()) {
                for (JsonNode n : stepsNode) {
                    steps.add(n.asText());
                }
            }
            return new PlanResponse(steps);
            
        } catch (RestClientResponseException exception) {
            throw new LlmProviderException(name + " API plan request failed with status " + exception.getStatusCode() + ": " + exception.getResponseBodyAsString(), exception);
        } catch (RestClientException exception) {
            throw new LlmProviderException(name + " API plan request failed (Connection error)", exception);
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException(name + " returned malformed JSON during planning", exception);
        }
    }

    private Map<String, Object> buildRequestBody(LlmRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, Object>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.systemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
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

    private LlmResponse parseResponse(String responseBody) {
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
            return new LlmResponse(content, name(), false, parseToolCalls(message.path("tool_calls")));
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException(name + " returned malformed JSON", exception);
        }
    }

    private Map<String, Object> toOpenAiTool(LlmToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        function.put("description", tool.description());
        function.put("parameters", tool.parameters());
        return Map.of("type", "function", "function", function);
    }

    private Map<String, Object> toOpenAiMessage(LlmMessage message) {
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

    private Map<String, Object> toOpenAiToolCall(LlmToolCall toolCall) {
        try {
            return Map.of("id", toolCall.id(), "type", "function", "function", Map.of(
                    "name", toolCall.name(), "arguments", objectMapper.writeValueAsString(toolCall.arguments())));
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException("Could not serialize tool call history", exception);
        }
    }

    private String serializeObservation(LlmToolObservation observation) {
        try {
            return objectMapper.writeValueAsString(observation.asMap());
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException("Could not serialize tool observation history", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private List<LlmToolCall> parseToolCalls(JsonNode toolCalls) throws JsonProcessingException {
        if (toolCalls.isMissingNode() || toolCalls.isNull()) return List.of();
        if (!toolCalls.isArray()) throw new LlmProviderException(name + " response has invalid tool_calls");
        List<LlmToolCall> parsed = new ArrayList<>();
        for (JsonNode toolCall : toolCalls) {
            JsonNode function = toolCall.path("function");
            if (!function.isObject() || !function.path("name").isTextual() || !function.path("arguments").isTextual()) {
                throw new LlmProviderException(name + " response has malformed tool call");
            }
            JsonNode arguments = objectMapper.readTree(function.path("arguments").asText());
            if (!arguments.isObject()) throw new LlmProviderException("Tool call arguments must be a JSON object");
            parsed.add(new LlmToolCall(toolCall.path("id").asText(""), function.path("name").asText(), objectMapper.convertValue(arguments, Map.class)));
        }
        return List.copyOf(parsed);
    }
}
