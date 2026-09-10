package dev.mark.tool.dto;

import java.util.Map;

/**
 * **1. What the class does:**
 * Represents an immutable Data Transfer Object (DTO) that encapsulates the outcome of a tool's execution.
 *
 * **2. Why it is useful:**
 * Standardizes tool execution responses across the application, ensuring thread safety and immutability of metadata while providing a clean API for success states.
 *
 * **3. How it fits in the flow:**
 * Acts as the return payload from tool execution components back to the orchestrator or LLM agent, signaling the result of an action.
 *
 * **4. Methods and variables:**
 * - `successful`: Boolean flag indicating if the tool executed without errors.
 * - `observation`: String containing the primary output or message from the tool.
 * - `metadata`: Map containing auxiliary contextual data.
 * - `ToolResultDTO(...)` (Constructor): Compact constructor that enforces immutability by creating an unmodifiable copy of the metadata.
 * - `success(String)`: Static factory method to quickly instantiate a successful result with empty metadata.
 *
 * **5. Logic and overall fit:**
 * The internal logic guarantees data integrity through defensive copying of metadata. In the overall application logic, callers inspect the `successful` flag to determine flow control, while the `observation` is typically fed back to the LLM or user.
 */

public record ToolResultDTO(boolean successful, String observation, Map<String, Object> metadata) {
    public ToolResultDTO { metadata = Map.copyOf(metadata); }
    public static ToolResultDTO success(String observation) { return new ToolResultDTO(true, observation, Map.of()); }
}
