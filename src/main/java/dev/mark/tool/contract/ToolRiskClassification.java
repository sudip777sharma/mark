package dev.mark.tool.contract;

/**
 * Risk classification for tool operations.
 * Determines authorization requirements, sandbox boundaries, and logging depth.
 */
public enum ToolRiskClassification {
    /** Safe, read-only operations that do not modify local or external state. */
    READ_ONLY,

    /** Safe internal operations (e.g. calculator, local in-memory echo). */
    SAFE_INTERNAL,

    /** File mutations strictly contained within the workspace sandbox. */
    WORKSPACE_MUTATION,

    /** Memory mutations stored in Mark's local database. */
    MEMORY_MUTATION,

    /** External communications (e.g. WhatsApp, emails, external web requests). */
    EXTERNAL_COMMUNICATION,

    /** Consequential system operations (e.g. launching processes, desktop mouse/keyboard input). */
    SYSTEM_CONSEQUENTIAL,

    /** High-risk destructive actions (e.g. file deletion, process killing, mass edits). */
    HIGH_RISK_DESTRUCTIVE;

    public boolean requiresExplicitAuthorization() {
        return this == HIGH_RISK_DESTRUCTIVE || this == EXTERNAL_COMMUNICATION;
    }

    public boolean isConsequential() {
        return this != READ_ONLY && this != SAFE_INTERNAL;
    }
}
