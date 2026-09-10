package dev.mark.agent.model;

/**
 * - **Purpose**: Represents the outcome of an agent verification process,
 * including its status and explanation.
 * - **Utility**: Encapsulates validation results in a standardized and
 * immutable format.
 * - **Application Flow**: Acts as the return type for agent validation checks
 * and security gates.
 * - **Components**: Contains a boolean verified flag and a String reason field,
 * along with success and failure factory methods for instantiation.
 * - **Logic Integration**: Passes explicit verification states downstream to
 * drive decision-making components.
 */

public record AgentVerificationResultModel(boolean verified, String reason) {
    public static AgentVerificationResultModel success() {
        return new AgentVerificationResultModel(true, "verified");
    }

    public static AgentVerificationResultModel failure(String reason) {
        return new AgentVerificationResultModel(false, reason);
    }
}
