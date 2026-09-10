package dev.mark.tool.dto;

import java.util.Map;
import java.util.UUID;

/**
 * - What it does: Represents an immutable Data Transfer Object (DTO) capturing a request to execute a specific tool or action.
 * - Why it is useful: Ensures thread-safe, read-only encapsulation of request data, preventing accidental parameter modification during processing.
 * - Application Flow: Acts as the entry-point payload from controllers or message consumers, passing down to the service layer for tool execution.
 * - Variables and Methods: Contains `taskId` for tracking, `action` to identify the operation, and `arguments` parameters. The compact constructor enforces immutability via `Map.copyOf`.
 * - Logic and Integration: Secures input data upon instantiation. Application logic reads this DTO to route execution to the appropriate tool handler based on the action and arguments.
 */

/**
 * **What it does:**
 * Represents an immutable Data Transfer Object (DTO) that captures a request to execute a specific tool or action.
 *
 * **Why it is useful:**
 * It ensures thread-safe, read-only, and structured encapsulation of tool request data, preventing accidental modification of request parameters during processing.
 *
 * **Application Flow:**
 * Acts as the entry-point payload received by controllers or message consumers, which is then passed down to the service layer to trigger the corresponding tool execution.
 *
 * **Variables and Methods:**
 * - `taskId`: UUID that uniquely identifies and tracks the execution of the task.
 * - `action`: String identifying the specific tool or operation to be executed.
 * - `arguments`: Map of key-value pairs containing the parameters required for the action.
 * - Compact Constructor: Automatically creates an unmodifiable copy of the `arguments` map to enforce immutability.
 *
 * **Logic and Integration:**
 * Upon instantiation, the record secures the input data by copying the arguments map. The application logic then reads this DTO to route the execution to the appropriate tool handler based on the `action` field, using the `arguments` as inputs.
 */

public record ToolRequestDTO(UUID taskId, String action, Map<String, Object> arguments) {
    public ToolRequestDTO { arguments = Map.copyOf(arguments); }
}
