package dev.mark.tool.core;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.llm.dto.LlmToolDefinitionDTO;
import java.util.Map;

/**
 * Represents the standard contract for executable tools within the MARK framework.
 *
 * - What it does: Defines a common interface for creating pluggable, executable tools that can be dynamically invoked by the system.
 * - Why it is useful: Standardizes tool definitions, enabling LLMs and orchestrators to discover, understand, and execute backend capabilities uniformly.
 * - Application flow: Acts as the bridge between LLM tool-calling requests and backend business logic by exposing metadata and routing executions.
 * - Methods and usefulness:
 *   - name(): Returns the unique identifier used for routing.
 *   - description(): Provides a natural language explanation for LLM context.
 *   - parameterSchema(): Defines the expected input structure for valid arguments.
 *   - execute(ToolRequestDTO): Performs the core business logic and returns a structured ToolResultDTO.
 * - Logic and integration: Concrete implementations handle specific tasks. The application matches requested tools by name, validates inputs against the schema, executes the logic, and returns results to complete the LLM interaction loop.
 */

/**
 * Represents the standard contract for executable tools within the MARK framework.
 *
 * 1. What it does:
 * Defines a common interface for creating pluggable, executable tools that can be dynamically invoked by the system.
 *
 * 2. Why it is useful:
 * Standardizes tool definitions, enabling Large Language Models (LLMs) or system orchestrators to discover, understand, and execute backend capabilities in a uniform manner.
 *
 * 3. How it fits in the flow:
 * Acts as the bridge between LLM tool-calling requests and backend business logic. The application registers these tools, exposes their metadata to the LLM, and routes execution requests to the matching implementation.
 *
 * 4. Methods and their usefulness:
 * - name(): Returns the unique identifier of the tool, used for routing.
 * - description(): Provides a natural language description so the LLM knows when to use the tool.
 * - parameterSchema(): Defines the JSON schema of expected inputs, ensuring the LLM formats arguments correctly.
 * - execute(ToolRequestDTO): Performs the actual business logic using the input payload and returns a structured ToolResultDTO.
 *
 * 5. Logic and overall application integration:
 * Concrete classes implement this interface to perform specific tasks (e.g., database lookups, API calls). When the LLM requests a tool execution, the application matches the tool by name, validates the input against the parameterSchema, runs the execute method, and returns the result back to the LLM to complete the interaction loop.
 */

public interface Tool {
    String name();
    default String description() { return "MARK tool: " + name(); }
    default Map<String, Object> parameterSchema() { return Map.of("type", "object", "properties", Map.of()); }
    ToolResultDTO execute(ToolRequestDTO request);
}
