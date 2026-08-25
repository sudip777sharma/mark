package dev.mark.llm;

import java.util.List;

public record LlmResponse(String content, String provider, boolean placeholder, List<LlmToolCall> toolCalls) {
    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public LlmResponse(String content, String provider, boolean placeholder) {
        this(content, provider, placeholder, List.of());
    }
}
