package dev.mark.agent.dto;

import dev.mark.agent.model.AgentIntentType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InteractionResponseDTOTest {

    @Test
    void shouldCreateChatResponse() {
        String replyMessage = "Hello there!";
        InteractionResponseDTO response = InteractionResponseDTO.chat(replyMessage);

        assertEquals(AgentIntentType.CHAT, response.intent());
        assertEquals(replyMessage, response.reply());
        assertNull(response.taskId());
        assertFalse(response.isTask());
    }

    @Test
    void shouldCreateKnowledgeQaResponse() {
        String qaMessage = "The capital of France is Paris.";
        InteractionResponseDTO response = InteractionResponseDTO.knowledgeQa(qaMessage);

        assertEquals(AgentIntentType.KNOWLEDGE_QA, response.intent());
        assertEquals(qaMessage, response.reply());
        assertNull(response.taskId());
        assertFalse(response.isTask());
    }

    @Test
    void shouldCreateTaskResponse() {
        String taskId = "task-12345";
        String taskMessage = "Starting background task...";
        InteractionResponseDTO response = InteractionResponseDTO.task(taskId, taskMessage);

        assertEquals(AgentIntentType.AUTONOMOUS_TASK, response.intent());
        assertEquals(taskMessage, response.reply());
        assertEquals(taskId, response.taskId());
        assertTrue(response.isTask());
    }
}
