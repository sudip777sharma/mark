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

        boolean hasSuccessfulToolObservation =
                observations.stream()
                        .anyMatch(LlmToolObservation::successful);

        if (hasSuccessfulToolObservation) {
            return VerificationResult.success();
        }

        if (worldState == null || completionContent == null) {
            return VerificationResult.failure(
                    "Completion has no successful tool observation or usable world state");
        }

        if (containsWorldStateValue(completionContent, worldState)) {
            return VerificationResult.success();
        }

        return VerificationResult.failure(
                "Completion is not supported by available world state");
    }

    private boolean containsWorldStateValue(
            String completionContent,
            WorldState worldState) {

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