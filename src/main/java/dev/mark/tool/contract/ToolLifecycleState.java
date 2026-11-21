package dev.mark.tool.contract;

/**
 * Standard lifecycle states for tool execution within MARK.
 * Enforces verified transitions from discovery to postcondition verification.
 */
public enum ToolLifecycleState {
    REGISTERED,
    DISCOVERED,
    SELECTED,
    ARGUMENTS_VALIDATED,
    PRECONDITIONS_VERIFIED,
    AUTHORIZATION_VERIFIED,
    EXECUTING,
    RESULT_VALIDATED,
    POSTCONDITIONS_VERIFIED,
    COMPLETED,

    // Terminal / Interrupted states
    UNKNOWN_TOOL,
    VALIDATION_FAILED,
    PRECONDITION_FAILED,
    AUTHORIZATION_REQUIRED,
    AUTHORIZATION_DENIED,
    EXECUTION_FAILED,
    TIMED_OUT,
    CANCELLED,
    PARTIALLY_COMPLETED,
    OUTCOME_UNKNOWN,
    VERIFICATION_FAILED;

    public boolean isTerminal() {
        return this == COMPLETED
                || this == UNKNOWN_TOOL
                || this == VALIDATION_FAILED
                || this == PRECONDITION_FAILED
                || this == AUTHORIZATION_DENIED
                || this == EXECUTION_FAILED
                || this == TIMED_OUT
                || this == CANCELLED
                || this == VERIFICATION_FAILED;
    }

    public boolean isSuccess() {
        return this == COMPLETED || this == POSTCONDITIONS_VERIFIED;
    }
}
