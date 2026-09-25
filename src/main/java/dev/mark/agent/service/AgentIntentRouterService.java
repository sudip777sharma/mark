package dev.mark.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.agent.dto.InteractionResponseDTO;
import dev.mark.agent.model.AgentIntentType;
import dev.mark.llm.dto.LlmMessageDTO;
import dev.mark.llm.dto.LlmRequestDTO;
import dev.mark.llm.dto.LlmResponseDTO;
import dev.mark.llm.service.LlmRouterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Intelligent Intent Classifier & Router.
 *
 * Evaluates incoming user inputs (voice transcripts or typed prompts) and dynamically
 * categorizes them into CHAT, KNOWLEDGE_QA, or AUTONOMOUS_TASK.
 *
 * Prevents casual greetings and simple questions from creating artificial database tasks
 * or triggering heavy autonomous multi-step planning loops.
 */
@Service
public class AgentIntentRouterService {

    private static final Logger log = LoggerFactory.getLogger(AgentIntentRouterService.class);

    private final LlmRouterService llmRouter;
    private final ObjectMapper objectMapper;

    private static final String INTENT_SYSTEM_PROMPT = """
        You are the intelligent intent classifier and interactive conversational dispatcher for MARK, an autonomous desktop agent.
        Analyze the user's input and classify it into EXACTLY ONE of three intents:

        1. CHAT:
           - Greetings, goodbyes, small talk, pleasantries, gratitude, or questions about who you are.
           - Examples: "hello", "hi MARK", "good morning", "thanks", "how are you doing", "who are you?", "what is your name?".
           - Action: Generate a friendly, concise, natural response directly in the 'reply' field.

        2. KNOWLEDGE_QA:
           - General knowledge questions, explanations, coding advice, or conceptual queries that can be answered directly using your internal knowledge WITHOUT executing computer tools, touching the filesystem, or controlling the desktop.
           - Examples: "what is Java 21?", "explain recursion", "how does quicksort work?", "what does NASA stand for?", "difference between thread and process".
           - Action: Generate a clear, concise factual answer directly in the 'reply' field.

        3. AUTONOMOUS_TASK:
           - Actionable goals, commands, or multi-step missions requiring tool execution, file creation/editing/reading, calculations, browser navigation, or desktop window automation.
           - Examples: "calculate 15 * 8 and save to file.txt", "open Chrome and search for flights", "list files in my workspace", "organize downloads", "type this into Notepad", "inspect my screen".
           - Action: Set 'reply' to null and let the autonomous engine plan and execute it.

        You MUST reply with ONLY a single raw JSON object in this exact format (no markdown formatting, no code blocks):
        {"intent": "CHAT" | "KNOWLEDGE_QA" | "AUTONOMOUS_TASK", "reply": "<text or null>"}
        """;

    public AgentIntentRouterService(LlmRouterService llmRouter, ObjectMapper objectMapper) {
        this.llmRouter = llmRouter;
        this.objectMapper = objectMapper;
    }

    /**
     * Evaluates the input and returns classification with direct response or task signal.
     */
    public InteractionResponseDTO classifyAndRoute(String input) {
        if (input == null || input.isBlank()) {
            return InteractionResponseDTO.chat("I didn't catch that. How can I assist you?");
        }

        String trimmed = input.trim();
        long startTime = System.currentTimeMillis();
        log.info(">>> [INTENT:EVAL] input='{}'", trimmed.length() > 80 ? trimmed.substring(0, 80) + "..." : trimmed);

        LlmRequestDTO request = new LlmRequestDTO(
                null,
                INTENT_SYSTEM_PROMPT,
                "Classify this input:\n\"" + trimmed + "\"",
                List.of(),
                "none",
                List.of(LlmMessageDTO.userMessage("Classify: " + trimmed))
        );

        try {
            LlmResponseDTO response = null;
            for (int i = 0; i < 5; i++) {
                try {
                    response = llmRouter.complete(request);
                    break;
                } catch (dev.mark.llm.exception.LlmProviderException e) {
                    if (e.getMessage() != null && e.getMessage().contains("429")) {
                        log.warn("--- [INTENT:429] Rate limited on key index {}, rotating...", i);
                        
                    } else {
                        throw e;
                    }
                }
            }
            if (response == null) throw new dev.mark.llm.exception.LlmProviderException("429 Quota Exhausted across all keys");
            long duration = System.currentTimeMillis() - startTime;

            String rawContent = response.content() != null ? response.content().trim() : "";
            // Clean markdown fences if present
            if (rawContent.startsWith("```json")) {
                rawContent = rawContent.substring(7);
            } else if (rawContent.startsWith("```")) {
                rawContent = rawContent.substring(3);
            }
            if (rawContent.endsWith("```")) {
                rawContent = rawContent.substring(0, rawContent.length() - 3);
            }
            rawContent = rawContent.trim();

            JsonNode node = objectMapper.readTree(rawContent);
            String intentStr = node.path("intent").asText("AUTONOMOUS_TASK").toUpperCase();
            String reply = node.hasNonNull("reply") ? node.path("reply").asText() : null;

            AgentIntentType intentType;
            try {
                intentType = AgentIntentType.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                intentType = AgentIntentType.AUTONOMOUS_TASK;
            }

            log.info("<<< [INTENT:RESULT] intent={} durationMs={} replyPreview='{}'",
                intentType, duration, reply != null ? (reply.length() > 60 ? reply.substring(0, 60) + "..." : reply) : "none");

            return switch (intentType) {
                case CHAT -> InteractionResponseDTO.chat(reply != null && !reply.isBlank() ? reply : "Hello! How can I assist you today?");
                case KNOWLEDGE_QA -> InteractionResponseDTO.knowledgeQa(reply != null && !reply.isBlank() ? reply : "Here is the information you requested.");
                case AUTONOMOUS_TASK -> InteractionResponseDTO.task(null, "Autonomous mission identified. Initializing execution plan...");
            };

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.warn("!!! [INTENT:FALLBACK] durationMs={} error='{}' -> applying heuristic fallback", duration, e.getMessage());
            return applyHeuristicFallback(trimmed);
        }
    }

    /**
     * Graceful fallback in case of network or LLM serialization failure.
     */
    private InteractionResponseDTO applyHeuristicFallback(String input) {
        String lower = input.toLowerCase().trim();

        if (lower.equals("hi") || lower.equals("hello") || lower.startsWith("hello ") || lower.startsWith("hi ")
                || lower.startsWith("hey") || lower.startsWith("good morning") || lower.startsWith("good evening")
                || lower.equals("thanks") || lower.equals("thank you")) {
            return InteractionResponseDTO.chat("Hello! I'm MARK, your autonomous agent. What would you like to work on?");
        }

        if ((lower.startsWith("what is ") || lower.startsWith("who is ") || lower.startsWith("explain "))
                && !lower.contains("file") && !lower.contains("desktop") && !lower.contains("browser") && !lower.contains("calculate")) {
            return InteractionResponseDTO.knowledgeQa("I'm ready to explain that. Let me look up the details for you.");
        }

        return InteractionResponseDTO.task(null, "Autonomous task detected. Launching agent orchestrator...");
    }
}
