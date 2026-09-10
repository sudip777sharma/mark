package dev.mark.llm.service;


import dev.mark.llm.dto.*;
import dev.mark.llm.exception.LlmProviderException;
    /**
     * **Purpose**: Defines a contract for interacting with LLM providers to generate text completions and execution plans.
     *
     * **Utility**: Abstracts provider-specific implementations, allowing the application to switch or add LLM vendors without modifying core business logic.
     *
     * **Application Flow**: Serves as the service layer interface called by controllers or orchestrators to process AI-driven requests.
     *
     * **Components**:
     * - name(): Returns the unique identifier of the provider.
     * - complete(): Sends a prompt and returns a standard LlmResponseDTO.
     * - plan(): Processes a request to generate a structured PlanResponseDTO.
     *
     * **Integration Logic**: Acts as the abstraction boundary between domain logic and external AI APIs, ensuring uniform DTO mapping and error handling via LlmProviderException.
     */

/**
 * - What it does: Defines a contract for interacting with Large Language Model (LLM) providers to generate text completions and execution plans.
 * - Why it is useful: Abstracts provider-specific implementations, allowing the application to switch or add LLM vendors seamlessly without changing core business logic.
 * - How it fits in the application flow: Acts as the service layer interface that controllers or higher-level orchestrators call when processing AI-driven requests.
 * - Methods and variables:
 *   - name(): Returns the unique identifier of the LLM provider.
 *   - complete(LlmRequestDTO): Sends a prompt request and returns a standard LlmResponseDTO.
 *   - plan(LlmRequestDTO): Processes a request to generate a structured PlanResponseDTO.
 * - Logic and integration: Serves as the abstraction boundary between the application's domain logic and external AI APIs, ensuring uniform error handling via LlmProviderException and standard DTO mapping.
 */

public interface LlmProviderService {
    String name();
    LlmResponseDTO complete(LlmRequestDTO request);
    PlanResponseDTO plan(LlmRequestDTO request);
}
