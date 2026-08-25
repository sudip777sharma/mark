package dev.mark.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentStateTest {
    @Test void permitsNormalStateTransitionsAndRetainsSteps() {
        AgentState state = new AgentState(UUID.randomUUID(), "test goal");
        state.transitionTo(AgentStatus.PLANNING);
        AgentStep step = new AgentStep(1, "LLM thought", "thought", "outcome", "system");
        state.addStep(step);
        state.transitionTo(AgentStatus.COMPLETED);
        assertEquals(AgentStatus.COMPLETED, state.status());
        assertEquals(1, state.steps().size());
    }
    @Test void rejectsTransitionFromTerminalState() {
        AgentState state = new AgentState(UUID.randomUUID(), "test goal");
        state.transitionTo(AgentStatus.COMPLETED);
        assertThrows(IllegalStateException.class, () -> state.transitionTo(AgentStatus.EXECUTING));
    }
}
