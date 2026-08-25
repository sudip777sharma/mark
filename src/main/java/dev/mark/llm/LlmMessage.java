package dev.mark.llm;

import java.util.List;

/** A serializable conversation entry sent back to the provider on later iterations. */
public record LlmMessage(String role, String content, String toolCallId, List<LlmToolCall> toolCalls, LlmToolObservation toolObservation, String base64Image, String mimeType) {
    public LlmMessage {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmMessage assistantToolCall(LlmToolCall toolCall) {
        return new LlmMessage("assistant", "", null, List.of(toolCall), null, null, null);
    }

    public static LlmMessage toolObservation(LlmToolObservation observation) {
        return new LlmMessage("tool", null, observation.toolCallId(), List.of(), observation, null, null);
    }

    public static LlmMessage assistantCompletion(String content) {
        return new LlmMessage("assistant", content, null, List.of(), null, null, null);
    }

    public static LlmMessage userMessage(String content) {
        return new LlmMessage("user", content, null, List.of(), null, null, null);
    }

    public static LlmMessage userMessageWithImage(String content, String base64Image, String mimeType) {
        return new LlmMessage("user", content, null, List.of(), null, base64Image, mimeType);
    }
}
