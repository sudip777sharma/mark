package dev.mark.llm.dto;

import java.util.List;

/**
 * Represents a serializable conversation entry exchanged with LLM providers across multi-turn iterations.
 *
 * - **What it does:** Encapsulates sender roles, text content, multimodal data, and tool interactions into a standard data transfer object.
 * - **Why it is useful:** Simplifies the creation, structure, and management of multi-turn conversation payloads for various LLM backends.
 * - **Application flow:** Passed from the application service layer to external LLM APIs to maintain active chat context.
 * - **Fields and methods:**
   - Fields (role, content, toolCallId, toolCalls, toolObservation, base64Image, mimeType) hold necessary message and multimodal metadata.
   - Factory methods (assistantToolCall, toolObservation, assistantCompletion, userMessage, userMessageWithImage) cleanly instantiate context-specific messages.
 * - **Logic integration:** Fits into conversation history tracking by using a canonical record constructor to safely normalize collections and enforce immutable formatting before network transmission.
 */

/**
 * A serializable conversation entry sent back to the provider on later
 * iterations.
 */
public record LlmMessageDTO(String role, String content, String toolCallId, List<LlmToolCallDTO> toolCalls,
        LlmToolObservationDTO toolObservation, String base64Image, String mimeType) {
    public LlmMessageDTO {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static LlmMessageDTO assistantToolCall(LlmToolCallDTO toolCall) {
        return new LlmMessageDTO("assistant", "", null, List.of(toolCall), null, null, null);
    }

    public static LlmMessageDTO toolObservation(LlmToolObservationDTO observation) {
        return new LlmMessageDTO("tool", null, observation.toolCallId(), List.of(), observation, null, null);
    }

    public static LlmMessageDTO assistantCompletion(String content) {
        return new LlmMessageDTO("assistant", content, null, List.of(), null, null, null);
    }

    public static LlmMessageDTO userMessage(String content) {
        return new LlmMessageDTO("user", content, null, List.of(), null, null, null);
    }

    public static LlmMessageDTO userMessageWithImage(String content, String base64Image, String mimeType) {
        return new LlmMessageDTO("user", content, null, List.of(), null, base64Image, mimeType);
    }
}
