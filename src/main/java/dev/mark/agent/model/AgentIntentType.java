package dev.mark.agent.model;

/**
 * Categorizes incoming user inputs to distinguish between conversational turns,
 * direct informational inquiries, and full autonomous multi-step tasks.
 */
public enum AgentIntentType {
    /**
     * Greetings, pleasantries, small talk, or polite remarks that require no tools.
     */
    CHAT,

    /**
     * Factual or conceptual questions answered directly from LLM knowledge without tools.
     */
    KNOWLEDGE_QA,

    /**
     * Actionable goals requiring planning, environment interaction, or tool execution.
     */
    AUTONOMOUS_TASK
}
