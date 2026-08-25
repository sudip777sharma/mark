package dev.mark.llm;

import java.util.List;

public record LlmRequest(String provider, String systemPrompt, String userPrompt, List<LlmToolDefinition> tools, Object toolChoice, List<LlmMessage> history) {
    public LlmRequest {
        tools = tools == null ? List.of() : List.copyOf(tools);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public LlmRequest(String provider, String systemPrompt, String userPrompt, List<LlmToolDefinition> tools, Object toolChoice) {
        this(provider, systemPrompt, userPrompt, tools, toolChoice, List.of());
    }

    public LlmRequest(String provider, String systemPrompt, String userPrompt) {
        this(provider, systemPrompt, userPrompt, List.of(), null);
    }
}
