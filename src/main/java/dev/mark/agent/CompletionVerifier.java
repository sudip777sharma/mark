package dev.mark.agent;

import dev.mark.llm.LlmToolObservation;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CompletionVerifier {
    public VerificationResult verify(List<LlmToolObservation> observations) {
        boolean hasSuccessfulObservation = observations.stream().anyMatch(LlmToolObservation::successful);
        return hasSuccessfulObservation
                ? VerificationResult.success()
                : VerificationResult.failure("Completion has no successful tool observation to verify");
    }
}
