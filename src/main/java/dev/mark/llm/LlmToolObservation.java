package dev.mark.llm;

import java.util.Map;

public record LlmToolObservation(String toolCallId, String toolName, boolean successful, String observation, Map<String, Object> metadata) {
    public LlmToolObservation {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> asMap() {
        return Map.of("tool", toolName, "successful", successful, "observation", observation, "metadata", metadata);
    }
}
