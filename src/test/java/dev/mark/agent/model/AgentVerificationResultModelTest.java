package dev.mark.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentVerificationResultModelTest {

    @Test
    void shouldCreateSuccessResult() {
        AgentVerificationResultModel result = AgentVerificationResultModel.success();

        assertTrue(result.verified());
        assertEquals("verified", result.reason());
    }

    @Test
    void shouldCreateFailureResult() {
        String failureReason = "Unauthorized access attempt.";
        AgentVerificationResultModel result = AgentVerificationResultModel.failure(failureReason);

        assertFalse(result.verified());
        assertEquals(failureReason, result.reason());
    }
}
