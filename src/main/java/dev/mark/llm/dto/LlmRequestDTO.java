package dev.mark.llm.dto;

import java.util.List;

public record LlmRequestDTO(String taskId, String phase, Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice, List<LlmMessageDTO> history) {
    public LlmRequestDTO {
        tools = tools == null ? List.of() : List.copyOf(tools);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public LlmRequestDTO(String taskId, String phase, Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice) {
        this(taskId, phase, configId, systemPrompt, userPrompt, tools, toolChoice, List.of());
    }

    public LlmRequestDTO(String taskId, String phase, Long configId, String systemPrompt, String userPrompt) {
        this(taskId, phase, configId, systemPrompt, userPrompt, List.of(), null);
    }
    
    // Backwards compatible constructors for tests or unspecified tasks
    public LlmRequestDTO(Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice, List<LlmMessageDTO> history) {
        this(null, "Unspecified", configId, systemPrompt, userPrompt, tools, toolChoice, history);
    }

    public LlmRequestDTO(Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice) {
        this(null, "Unspecified", configId, systemPrompt, userPrompt, tools, toolChoice, List.of());
    }

    public LlmRequestDTO(Long configId, String systemPrompt, String userPrompt) {
        this(null, "Unspecified", configId, systemPrompt, userPrompt, List.of(), null);
    }
}
