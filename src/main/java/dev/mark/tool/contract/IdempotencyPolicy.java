package dev.mark.tool.contract;

/**
 * Idempotency classification for tool operations.
 * Dictates whether retry after timeout or failure is safe.
 */
public enum IdempotencyPolicy {
    /** Multiple identical invocations produce the exact same outcome without side effects. */
    NATURALLY_IDEMPOTENT,

    /** Can be made idempotent using an operation/idempotency key. */
    IDEMPOTENT_WITH_KEY,

    /** Invoking multiple times causes duplicate side effects (e.g. sending a message). NEVER blindly retry. */
    NON_IDEMPOTENT;

    public boolean isSafeToRetry() {
        return this == NATURALLY_IDEMPOTENT || this == IDEMPOTENT_WITH_KEY;
    }
}
