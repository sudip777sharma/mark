package dev.mark.eval.service;

import dev.mark.agent.model.AgentStatusModel;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.eval.model.EvalResultModel;
import dev.mark.eval.model.EvalScenarioModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvalRunnerServiceTest {

    private AgentOrchestratorService orchestratorService;
    private EvalRunnerService evalRunnerService;

    @BeforeEach
    void setUp() {
        orchestratorService = mock(AgentOrchestratorService.class);
        evalRunnerService = new EvalRunnerService(orchestratorService);
    }

    @Test
    void testSuccessfulEvaluation() {
        when(orchestratorService.executeTask(anyString(), eq("Say Hello")))
                .thenReturn(new TaskResponseDTO(java.util.UUID.randomUUID(), "Say Hello", AgentStatusModel.COMPLETED, "Hello World", "", List.of(), 0, List.of()));

        EvalScenarioModel scenario = new EvalScenarioModel("test-1", "Say Hello", "Hello.*");
        List<EvalResultModel> results = evalRunnerService.runEvaluations(List.of(scenario));

        assertEquals(1, results.size());
        assertTrue(results.get(0).passed());
        assertEquals(AgentStatusModel.COMPLETED, results.get(0).actualStatus());
    }

    @Test
    void testFailedEvaluationDueToRegex() {
        when(orchestratorService.executeTask(anyString(), eq("Say Hello")))
                .thenReturn(new TaskResponseDTO(java.util.UUID.randomUUID(), "Say Hello", AgentStatusModel.COMPLETED, "Goodbye World", "", List.of(), 0, List.of()));

        EvalScenarioModel scenario = new EvalScenarioModel("test-2", "Say Hello", "Hello.*");
        List<EvalResultModel> results = evalRunnerService.runEvaluations(List.of(scenario));

        assertEquals(1, results.size());
        assertFalse(results.get(0).passed());
        assertTrue(results.get(0).failureReason().contains("did not match expected regex"));
    }
}

