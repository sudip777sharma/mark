package dev.mark.agent.model;

/**
 * Represents the discrete operational lifecycle states of an autonomous AI
 * agent.
 *
 * - **What it does:** Defines standard state constants governing an agent from
 * creation through planning, execution, verification, and termination.
 * - **Why it is useful:** Provides a strongly typed, deterministic state
 * machine foundation that eliminates magic strings and ensures safe
 * serialization.
 * - **Application flow fit:** Acts as the primary state variable monitored by
 * orchestrators and schedulers to advance agents through their execution
 * phases.
 * - **Methods and variables:** Exposes constants (CREATED, PLANNING, EXECUTING,
 * VERIFYING, COMPLETED, FAILED, NEEDS_INPUT) alongside standard enum utilities
 * (values, valueOf) to track runtime checkpoints.
 * - **Logic and architecture fit:** Drives the execution engine's control loop,
 * updates the persistence layer, and powers real-time frontend monitoring.
 */

public enum AgentStatusModel {
    CREATED,
    PLANNING,
    EXECUTING,
    VERIFYING,
    COMPLETED,
    FAILED,
    NEEDS_INPUT
}
