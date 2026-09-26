package dev.mark.llm.dto;

import java.util.List;

/**
 * - What the class does: Acts as a standard Data Transfer Object (DTO) record representing a payload for LLM service requests.
 * - Why it is useful: Encapsulates all required request parameters such as provider, prompts, tools, and conversation history safely and concisely with immutable collections.
 * - How it fits in the flow of the application: Bridges the controller or service layer and the LLM client integration layer by standardizing outgoing request data.
 * - Its methods and variables and how they are useful: Contains fields for configId, systemPrompt, userPrompt, tools, toolChoice, and history, along with compact constructors that ensure null-safe, unmodifiable lists.
 * - Its logic and how it gets fit into the overall application logic: Automatically normalizes null inputs for tools and history into empty lists upon instantiation, ensuring robust and predictable behavior when dispatching payloads to external AI providers.
 */

public record LlmRequestDTO(Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice, List<LlmMessageDTO> history) {
    public LlmRequestDTO {
        tools = tools == null ? List.of() : List.copyOf(tools);
        history = history == null ? List.of() : List.copyOf(history);
    }

    public LlmRequestDTO(Long configId, String systemPrompt, String userPrompt, List<LlmToolDefinitionDTO> tools, Object toolChoice) {
        this(configId, systemPrompt, userPrompt, tools, toolChoice, List.of());
    }

    public LlmRequestDTO(Long configId, String systemPrompt, String userPrompt) {
        this(configId, systemPrompt, userPrompt, List.of(), null);
    }
}
