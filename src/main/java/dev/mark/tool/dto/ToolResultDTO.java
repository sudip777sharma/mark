package dev.mark.tool.dto;

import java.util.Map;

/**
 * **1. What the class does:**
 * Represents an immutable Data Transfer Object (DTO) that encapsulates the outcome of a tool's execution.
 *
 * **2. Why it is useful:**
 * Standardizes tool execution responses across the application, ensuring thread safety and immutability of metadata while providing a clean API for success states and typed human intervention requests.
 *
 * **3. How it fits in the flow:**
 * Acts as the return payload from tool execution components back to the orchestrator or LLM agent, signaling the result of an action.
 *
 * **4. Methods and variables:**
 * - `successful`: Boolean flag indicating if the tool executed without errors.
 * - `observation`: String containing the primary output or message from the tool.
 * - `metadata`: Map containing auxiliary contextual data.
 * - `interventionRequest`: Optional typed HumanInterventionRequest when human involvement is needed.
 * - `ToolResultDTO(...)`: Canonical constructor enforcing immutability.
 * - `success(String)`: Static factory method for successful result.
 * - `intervention(...)`: Static factory method for requesting human action.
 */
public record ToolResultDTO(
    boolean successful,
    String observation,
    Map<String, Object> metadata,
    HumanInterventionRequest interventionRequest
) {
    public ToolResultDTO {
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    public ToolResultDTO(boolean successful, String observation, Map<String, Object> metadata) {
        this(successful, observation, metadata, null);
    }

    public static ToolResultDTO success(String observation) {
        return new ToolResultDTO(true, observation, Map.of(), null);
    }

    public static ToolResultDTO intervention(HumanInterventionRequest intervention, String observation, Map<String, Object> metadata) {
        return new ToolResultDTO(false, observation, metadata, intervention);
    }
}
