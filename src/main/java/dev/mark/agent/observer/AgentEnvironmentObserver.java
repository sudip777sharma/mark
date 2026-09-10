package dev.mark.agent.observer;

import dev.mark.agent.model.AgentWorldStateModel;

/**
 * Defines the sensory and observation layer for autonomous agents by capturing
 * environmental state into a structured model.
 *
 * - **What it does:** Acts as a perceptual abstraction that reads raw external
 * signals, telemetry, or data and translates them into an
 * {@link AgentWorldStateModel}.
 * - **Why it is useful:** Decouples data ingestion logic from agent reasoning,
 * enhances testability via mocking, and enables polymorphic adaptation across
 * different deployment environments.
 * - **Application Flow:** Operates at the very beginning of the
 * Sense-Deliberate-Act cycle, where an execution loop triggers perception,
 * generates the state model, and passes it to downstream deliberation engines.
 * - **Methods and Variables:** Declares the `observe()` method to poll and
 * return an updated world snapshot. Implementing classes manage connection
 * clients, caches, and configurations to fetch and filter environmental data.
 * - **System Logic Integration:** Serves as the primary data producer,
 * ingesting external raw streams and emitting standardized state objects that
 * drive subsequent cognitive decisions and actuator responses.
 *
 * @see dev.mark.agent.model.AgentWorldStateModel
 */

public interface AgentEnvironmentObserver {

    AgentWorldStateModel observe();
}
