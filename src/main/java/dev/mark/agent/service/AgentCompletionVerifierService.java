package dev.mark.agent.service;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.*;
import dev.mark.llm.dto.*;
import dev.mark.llm.dto.LlmToolObservationDTO;
import dev.mark.agent.model.AgentVerificationResultModel;
import dev.mark.agent.model.AgentWorldStateModel;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * - What the class does: Verifies whether an AI agent has successfully achieved its assigned goal based on execution history and world state.
 * - Why it is useful: Prevents hallucinated completions by validating agent outputs against concrete evidence like successful tool usage, direct response patterns, or world state updates.
 * - How it fits in the flow of the application: Acts as a post-execution quality gate before concluding an agent task loop.
 * - Its methods and variables: The verify method evaluates messages and world states to return an AgentVerificationResultModel, supported by helper methods like isDirectResponseGoal and containsWorldStateValue to validate specific completion criteria.
 * - Its logic and how it fits into the overall application logic: Evaluates the final LLM message against strict criteria, prioritizing successful tool observations, matching informational queries, or confirming world state alignment to decide task success or failure.
 */

@Component
public class AgentCompletionVerifierService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AgentCompletionVerifierService.class);

    public AgentVerificationResultModel verify(
            String goal,
            List<LlmMessageDTO> messages,
            AgentWorldStateModel worldState) {

        String completionContent = messages.isEmpty() ? "" : messages.get(messages.size() - 1).content();
        if (completionContent == null || completionContent.isBlank()) {
            log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Completion is empty'", goal);
            return AgentVerificationResultModel.failure("Completion is empty");
        }

        boolean hasSuccessfulToolObservation =
                messages.stream()
                        .filter(m -> "tool".equals(m.role()) && m.toolObservation() != null)
                        .anyMatch(m -> m.toolObservation().successful());

        log.info(">>> [VERIFIER:EVAL] goal='{}' toolObsCount={} hasSuccessfulTool={}",
            goal, messages.stream().filter(m -> "tool".equals(m.role())).count(), hasSuccessfulToolObservation);

        if (hasSuccessfulToolObservation) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by successful tool observation", goal);
            return AgentVerificationResultModel.success();
        }

        // A direct textual response is valid when no tool was required.
        if (isDirectResponseGoal(goal)) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated as direct informational or conversational response", goal);
            return AgentVerificationResultModel.success();
        }

        if (worldState != null && containsWorldStateValue(completionContent, worldState)) {
            log.info("<<< [VERIFIER:SUCCESS] goal='{}' validated by world state observation match", goal);
            return AgentVerificationResultModel.success();
        }

        log.warn("<<< [VERIFIER:FAILED] goal='{}' reason='Completion is not supported by available evidence'", goal);
        return AgentVerificationResultModel.failure(
                "Completion is not supported by available evidence");
    }

    private boolean isDirectResponseGoal(String goal) {
        if (goal == null || goal.isBlank()) {
            return false;
        }

        String normalized = goal.toLowerCase().trim();

        return normalized.startsWith("say ")
                || normalized.startsWith("tell me ")
                || normalized.startsWith("answer ")
                || normalized.startsWith("what is ")
                || normalized.startsWith("who is ")
                || normalized.startsWith("where is ")
                || normalized.startsWith("when is ")
                || normalized.startsWith("how ")
                || normalized.startsWith("explain ")
                || normalized.startsWith("describe ")
                || normalized.startsWith("hello")
                || normalized.startsWith("hi")
                || normalized.startsWith("hey")
                || normalized.startsWith("good morning")
                || normalized.startsWith("good evening");
    }

    private boolean containsWorldStateValue(String completionContent, AgentWorldStateModel worldState) {

        String text = completionContent.toLowerCase();

        return contains(text, worldState.activeApplication())
                || contains(text, worldState.activeWindow())
                || contains(text, worldState.browserUrl())
                || contains(text, worldState.browserTitle());
    }

    private boolean contains(String text, String value) {
        return value != null
                && !value.isBlank()
                && text.contains(value.toLowerCase());
    }
}
