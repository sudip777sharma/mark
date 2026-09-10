package dev.mark.llm.dto;

import java.util.Map;

/**
 * - What it does: Represents a Data Transfer Object for Large Language Model tool calls, encapsulating the tool ID, name, arguments, and optional metadata.
 * - Why it is useful: Provides a structured, immutable data container to safely pass function-calling requests between services and LLM providers.
 * - Flow context: Acts as a bridge between raw LLM API responses and the application's internal execution engine for external tools.
 * - Methods and variables: Contains immutable fields and a compact constructor that guarantees safe, non-null defensive copies of argument and metadata maps.
 * - Application logic: Fits into the orchestration layer by parsing, storing, and forwarding requested tool parameters to execute dynamic AI-driven actions.
 */

/**
 * - What it does: Encapsulates Large Language Model tool call data including ID, name, arguments, and metadata.
 * - Why it is useful: Provides a structured, immutable container for safely passing function-calling requests.
 * - Flow context: Bridges raw LLM API responses and the application's internal tool execution engine.
 * - Methods and variables: Uses immutable fields and a compact constructor that ensures safe, non-null defensive map copies.
 * - Application logic: Integrates into the orchestration layer to parse, store, and forward dynamic AI tool parameters.
 */

/**
 * - What it does: Represents a Data Transfer Object for Large Language Model tool calls, encapsulating the tool ID, name, arguments, and optional metadata.
 * - Why it is useful: Provides a structured, immutable data container to safely pass function-calling requests between services and LLM providers.
 * - Flow context: Acts as a bridge between raw LLM API responses and the application's internal execution engine for external tools.
 * - Methods and variables: Contains immutable fields (id, name, arguments, metadata) and a compact constructor that guarantees safe, non-null defensive copies of maps.
 * - Application logic: Fits into the orchestration layer by parsing, storing, and forwarding requested tool parameters so the system can execute dynamic actions requested by the AI.
 */

/**
 * - What it does: Represents a Data Transfer Object for Large Language Model tool calls, encapsulating the tool ID, name, arguments, and optional metadata.
 * - Why it is useful: Provides a structured, immutable data container to safely pass function-calling requests between services and LLM providers.
 * - Flow context: Acts as a bridge between raw LLM API responses and the application's internal execution engine for external tools.
 * - Methods and variables: Contains immutable fields (id, name, arguments, metadata) and a compact constructor that guarantees safe, non-null defensive copies of maps.
 * - Application logic: Fits into the orchestration layer by parsing, storing, and forwarding requested tool parameters so the system can execute dynamic actions requested by the AI.
 */

public record LlmToolCallDTO(String id, String name, Map<String, Object> arguments, Map<String, Object> metadata) {
    public LlmToolCallDTO(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, Map.of());
    }

    public LlmToolCallDTO {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
