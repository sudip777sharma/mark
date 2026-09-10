package dev.mark.llm.dto;

import java.util.Map;

/**
 * Represents a data transfer object (DTO) that defines a specific tool or function
 * capable of being invoked by a Large Language Model (LLM). This immutable record
 * encapsulates the metadata required by modern LLM function-calling APIs.
 *
 * **Why it is useful:**
 * Provides a clean, type-safe, and immutable structure to serialize and transmit tool
 * metadata (name, description, and parameters) from the application layer to LLM provider
 * APIs for function calling.
 *
 * **Application Flow Integration:**
 * - Acts as a bridge between backend business services and the LLM orchestration layer.
 * - Backend services register available tools as instances of this record.
 * - An orchestration service collects these definitions to format payloads for LLM clients.
 * - The LLM evaluates user prompts against these definitions to request tool execution.
 *
 * **Variables and Their Logic:**
 * - name: Unique programmatic identifier used by the LLM to signal tool invocation.
 * - description: Text explaining the tool's purpose and usage to guide the LLM's reasoning engine.
 * - parameters: JSON Schema-compliant map defining accepted arguments.
 *
 * **Methods and Their Logic:**
 * - Compact Constructor: Enforces immutability and defensive copying by defaulting null parameters to empty maps and wrapping existing maps using Map.copyOf().
 * - Generated Record Methods (name, description, parameters, equals, hashCode, toString): Provide standard property access, comparison, and logging capabilities.
 *
 * @author Senior Java Developer
 * @version 1.0
 * @since 1.0
 * @see java.util.Map
 */

/**
* Represents an immutable data transfer object defining a tool or function that a Large Language Model (LLM) can invoke.
*
* **Why it is useful:**
* Provides a clean, type-safe, and immutable structure to serialize and transmit tool metadata (name, description, and parameters) to LLM provider APIs for function calling.
*
* **Application Flow Integration:**
* - Acts as a bridge between backend business services and the LLM orchestration layer.
* - Backend services register available tools as instances of this record.
* - An orchestration service collects these definitions to format payloads for LLM clients.
* - The LLM evaluates user prompts against these definitions to request tool execution.
*
* **Variables and Their Logic:**
* - name: Unique programmatic identifier used by the LLM to signal tool invocation.
* - description: Text explaining the tool's purpose and usage to guide the LLM's reasoning engine.
* - parameters: JSON Schema-compliant map defining accepted arguments.
*
* **Methods and Their Logic:**
* - Compact Constructor: Enforces immutability and defensive copying by defaulting null parameters to empty maps and wrapping existing maps using Map.copyOf().
* - Generated Record Methods (name, description, parameters, equals, hashCode, toString): Provide standard property access, comparison, and logging capabilities.
*
* @author Senior Java Developer
* @version 1.0
* @since 1.0
* @see java.util.Map
*/

/**
* Represents an immutable data transfer object defining a tool or function that a Large Language Model (LLM) can invoke.
*
* **Why it is useful:**
* Provides a clean, type-safe, and immutable structure to serialize and transmit tool metadata (name, description, and parameters) to LLM provider APIs for function calling.
*
* **Application Flow Integration:**
* - Acts as a bridge between backend business services and the LLM orchestration layer.
* - Backend services register available tools as instances of this record.
* - An orchestration service collects these definitions to format payloads for LLM clients.
* - The LLM evaluates user prompts against these definitions to request tool execution.
*
* **Variables and Their Logic:**
* - name: Unique programmatic identifier used by the LLM to signal tool invocation.
* - description: Text explaining the tool's purpose and usage to guide the LLM's reasoning engine.
* - parameters: JSON Schema-compliant map defining accepted arguments.
*
* **Methods and Their Logic:**
* - Compact Constructor: Enforces immutability and defensive copying by defaulting null parameters to empty maps and wrapping existing maps using Map.copyOf().
* - Generated Record Methods (name, description, parameters, equals, hashCode, toString): Provide standard property access, comparison, and logging capabilities.
*
* @author Senior Java Developer
* @version 1.0
* @since 1.0
* @see java.util.Map
*/

/**
* Represents an immutable data transfer object defining a tool or function that a Large Language Model (LLM) can invoke.
*
* **Why it is useful:**
* Provides a clean, type-safe, and immutable structure to serialize and transmit tool metadata (name, description, and parameters) from the application to LLM provider APIs for function calling.
*
* **Application Flow Integration:**
* - Acts as a bridge between backend business services and the LLM orchestration layer.
* - Backend services register available tools as instances of this record.
* - An orchestration service collects these definitions to format payloads for LLM clients.
* - The LLM evaluates user prompts against these definitions to request tool execution.
*
* **Variables and Their Logic:**
* - name: Unique programmatic identifier used by the LLM to signal tool invocation.
* - description: Text explaining the tool's purpose and usage to guide the LLM's reasoning engine.
* - parameters: JSON Schema-compliant map defining accepted arguments.
*
* **Methods and Their Logic:**
* - Compact Constructor: Enforces immutability and defensive copying by defaulting null parameters to empty maps and wrapping existing maps using Map.copyOf().
* - Generated Record Methods (name, description, parameters, equals, hashCode, toString): Provide standard property access, comparison, and logging capabilities.
*
* @author Senior Java Developer
* @version 1.0
* @since 1.0
* @see java.util.Map
*/

/**
 * Represents a data transfer object (DTO) that defines a specific tool or function
 * capable of being invoked by a Large Language Model (LLM). This immutable record
 * encapsulates the metadata required by modern LLM function-calling APIs.
 *
 * <h2>Why it is useful:</h2>
 * LLMs frequently require structured definitions of external tools, functions, or APIs
 * they can invoke to retrieve real-time data or perform actions. This class provides a
 * clean, type-safe, and immutable data structure to serialize and transmit tool
 * metadata (such as tool name, operational description, and schema parameters) from
 * the application layer to the LLM provider API.
 *
 * <h2>Application Flow Integration:</h2>
 * Within the overall architecture of the application, this DTO acts as a bridge between
 * the business services that register available capabilities and the LLM orchestration
 * layer. The flow typically proceeds as follows:
 * <ol>
 *   <li>Service components register available backend tools as instances of this record.</li>
 *   <li>An orchestration service collects these definitions and formats them into the
 *       payload expected by the LLM client (e.g., OpenAI, Anthropic).</li>
 *   <li>The LLM evaluates the user prompt against these definitions and returns a tool
 *       execution request if a match is found.</li>
 *   <li>The application parses the request, executes the corresponding backend logic,
 *       and feeds the result back into the conversation context.</li>
 * </ol>
 *
 * <h2>Variables and Their Logic:</h2>
 * <ul>
 *   <li>{@code String name}: The unique identifier or programmatic name of the tool.
 *       The LLM uses this exact string to signal which tool it wants to invoke.</li>
 *   <li>{@code String description}: Human-readable or model-optimized text explaining
 *       what the tool does, what inputs it expects, and when it should be used. This
 *       guides the LLM's reasoning engine during function selection.</li>
 *   <li>{@code Map<String, Object> parameters}: A JSON Schema-compliant map defining
 *       the arguments the tool accepts.</li>
 * </ul>
 *
 * <h2>Methods and Their Logic:</h2>
 * <ul>
 *   <li>{@code LlmToolDefinitionDTO(String, String, Map)} (Compact Constructor):
 *       Enforces defensive initialization and immutability guarantees. If the provided
 *       {@code parameters} map is null, it defaults to an empty, immutable map using
 *       {@code Map.of()}. Otherwise, it wraps the map using {@code Map.copyOf()} to
 *       prevent external mutations of the internal state, ensuring thread safety and
 *       predictable behavior throughout the application lifecycle.</li>
 *   <li>Generated Record Methods ({@code name()}, {@code description()}, {@code parameters()},
 *       {@code equals()}, {@code hashCode()}, {@code toString()}): Standard boilerplate
 *       methods provided by Java records for property access, comparison, and logging.</li>
 * </ul>
 *
 * @author Senior Java Developer
 * @version 1.0
 * @since 1.0
 * @see java.util.Map
 */

/**
 * Represents a data transfer object (DTO) that defines a specific tool or function
 * capable of being invoked by a Large Language Model (LLM). This immutable record
 * encapsulates the metadata required by modern LLM function-calling APIs.
 *
 * <h2>Why it is useful:</h2>
 * LLMs frequently require structured definitions of external tools, functions, or APIs
 * they can invoke to retrieve real-time data or perform actions. This class provides a
 * clean, type-safe, and immutable data structure to serialize and transmit tool
 * metadata (such as tool name, operational description, and schema parameters) from
 * the application layer to the LLM provider API.
 *
 * <h2>Application Flow Integration:</h2>
 * Within the overall architecture of the application, this DTO acts as a bridge between
 * the business services that register available capabilities and the LLM orchestration
 * layer. The flow typically proceeds as follows:
 * <ol>
 *   <li>Service components register available backend tools as instances of this record.</li>
 *   <li>An orchestration service collects these definitions and formats them into the
 *       payload expected by the LLM client (e.g., OpenAI, Anthropic).</li>
 *   <li>The LLM evaluates the user prompt against these definitions and returns a tool
 *       execution request if a match is found.</li>
 *   <li>The application parses the request, executes the corresponding backend logic,
 *       and feeds the result back into the conversation context.</li>
 * </ol>
 *
 * <h2>Variables and Their Logic:</h2>
 * <ul>
 *   <li>{@code String name}: The unique identifier or programmatic name of the tool.
 *       The LLM uses this exact string to signal which tool it wants to invoke.</li>
 *   <li>{@code String description}: Human-readable or model-optimized text explaining
 *       what the tool does, what inputs it expects, and when it should be used. This
 *       guides the LLM's reasoning engine during function selection.</li>
 *   <li>{@code Map<String, Object> parameters}: A JSON Schema-compliant map defining
 *       the arguments the tool accepts.</li>
 * </ul>
 *
 * <h2>Methods and Their Logic:</h2>
 * <ul>
 *   <li>{@code LlmToolDefinitionDTO(String, String, Map)} (Compact Constructor):
 *       Enforces defensive initialization and immutability guarantees. If the provided
 *       {@code parameters} map is null, it defaults to an empty, immutable map using
 *       {@code Map.of()}. Otherwise, it wraps the map using {@code Map.copyOf()} to
 *       prevent external mutations of the internal state, ensuring thread safety and
 *       predictable behavior throughout the application lifecycle.</li>
 *   <li>Generated Record Methods ({@code name()}, {@code description()}, {@code parameters()},
 *       {@code equals()}, {@code hashCode()}, {@code toString()}): Standard boilerplate
 *       methods provided by Java records for property access, comparison, and logging.</li>
 * </ul>
 *
 * @author Senior Java Developer
 * @version 1.0
 * @since 1.0
 * @see java.util.Map
 */

public record LlmToolDefinitionDTO(String name, String description, Map<String, Object> parameters) {
    public LlmToolDefinitionDTO {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
