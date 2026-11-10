package dev.mark.skill.service;

import dev.mark.skill.core.Skill;
import dev.mark.skill.dto.SkillExecutionResult;
import dev.mark.trajectory.service.TrajectoryRecorderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class L0SkillEngineTest {

    private Skill mockSkill;
    private TrajectoryRecorderService trajectoryRecorderService;
    private SemanticSkillMatcher semanticMatcher;
    private L0SkillEngine engine;

    @BeforeEach
    void setUp() {
        mockSkill = Mockito.mock(Skill.class);
        trajectoryRecorderService = Mockito.mock(TrajectoryRecorderService.class);
        semanticMatcher = new SemanticSkillMatcher();
        when(mockSkill.name()).thenReturn("mock.skill");

        engine = new L0SkillEngine(List.of(mockSkill), trajectoryRecorderService, semanticMatcher);
    }

    @Test
    void tryExecute_matchesDirectPrompt() {
        when(mockSkill.canHandle("direct command")).thenReturn(true);
        when(mockSkill.extractSlots("direct command")).thenReturn(Map.of("key", "val"));
        when(mockSkill.execute(eq("task-123"), any())).thenReturn(
                SkillExecutionResult.completed("Executed successfully", List.of())
        );

        Optional<SkillExecutionResult> res = engine.tryExecute("task-123", "direct command");

        assertTrue(res.isPresent());
        assertTrue(res.get().success());
        assertEquals("Executed successfully", res.get().summary());
    }

    @Test
    void tryExecute_matchesNormalizedPromptWithConversationalNoise() {
        when(mockSkill.canHandle("hey mark, send message please")).thenReturn(false);
        when(mockSkill.canHandle("send message")).thenReturn(true);
        when(mockSkill.extractSlots("send message")).thenReturn(Map.of("action", "send"));
        when(mockSkill.execute(eq("task-456"), any())).thenReturn(
                SkillExecutionResult.completed("Sent", List.of())
        );

        Optional<SkillExecutionResult> res = engine.tryExecute("task-456", "Hey Mark, send message please");

        assertTrue(res.isPresent());
        assertTrue(res.get().success());
    }

    @Test
    void tryExecute_returnsEmptyForUnmatchedGoal() {
        when(mockSkill.canHandle(any())).thenReturn(false);

        Optional<SkillExecutionResult> res = engine.tryExecute("task-789", "unsupported command");

        assertTrue(res.isEmpty());
    }
}
