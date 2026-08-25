package dev.mark.llm;

import java.util.Map;

public record LlmToolCall(String id, String name, Map<String, Object> arguments, Map<String, Object> metadata) {
    public LlmToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, Map.of());
    }

    public LlmToolCall {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
