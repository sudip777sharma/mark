package dev.mark.agent;

import dev.mark.llm.LlmToolObservation;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CompletionVerifier {

    public VerificationResult verify(
            String goal,
            String completionContent,
            List<LlmToolObservation> observations,
            WorldState worldState) {

        if (completionContent == null || completionContent.isBlank()) {
            return VerificationResult.failure("Completion is empty");
        }

        boolean hasSuccessfulToolObservation =
                observations.stream()
                        .anyMatch(LlmToolObservation::successful);

        if (hasSuccessfulToolObservation) {
            return VerificationResult.success();
        }

        // A direct textual response is valid when no tool was required.
        if (isDirectResponseGoal(goal)) {
            return VerificationResult.success();
        }

        if (worldState != null && containsWorldStateValue(completionContent, worldState)) {
            return VerificationResult.success();
        }

        return VerificationResult.failure(
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
                || normalized.startsWith("when is ");
    }

    private boolean containsWorldStateValue(String completionContent, WorldState worldState) {

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