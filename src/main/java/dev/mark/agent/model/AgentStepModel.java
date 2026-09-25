package dev.mark.agent.model;

/**
 * Represents a single execution step taken by an AI agent within the
 * application.
 *
 * - **Purpose**: Encapsulates data related to an individual agent action,
 * including its sequence number, description, invoked tool, result, and the AI
 * provider used.
 * - **Application Flow**: Acts as a lightweight data transfer and storage
 * object used when recording, tracking, or transmitting the execution history
 * of an agent workflow.
 * - **Components**:
 * - number: Identifies the sequential order of the step.
 * - description: Explains what the step aims to achieve.
 * - toolName: Specifies the tool utilized during execution.
 * - outcome: Stores the result or output of the step.
 * - provider: Identifies the AI service provider handling the request.
 * - **Logic Integration**: Fits into the overall logging and state-management
 * logic by providing a structured data model to audit, display, and serialize
 * agent progress across system layers.
 */

public record AgentStepModel(int number, String description, String toolName, String toolArguments, String outcome, String provider) {

}
