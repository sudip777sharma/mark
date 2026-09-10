package dev.mark.agent.dto;

import dev.mark.agent.model.AgentIntentType;

/**
 * Standardized response DTO returned by the Dynamic Intent Router.
 *
 * @param intent      Classified intent (CHAT, KNOWLEDGE_QA, AUTONOMOUS_TASK).
 * @param reply       Direct conversational answer or initial status statement.
 * @param taskId      The ID of the created TaskEntity if intent is AUTONOMOUS_TASK, otherwise null.
 * @param isTask      Convenience boolean indicating whether a background mission was launched.
 */
public record InteractionResponseDTO(
    AgentIntentType intent,
    String reply,
    String taskId,
    boolean isTask
) {
    public static InteractionResponseDTO chat(String reply) {
        return new InteractionResponseDTO(AgentIntentType.CHAT, reply, null, false);
    }

    public static InteractionResponseDTO knowledgeQa(String reply) {
        return new InteractionResponseDTO(AgentIntentType.KNOWLEDGE_QA, reply, null, false);
    }

    public static InteractionResponseDTO task(String taskId, String initialReply) {
        return new InteractionResponseDTO(AgentIntentType.AUTONOMOUS_TASK, initialReply, taskId, true);
    }
}
