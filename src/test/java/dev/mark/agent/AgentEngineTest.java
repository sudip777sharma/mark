package dev.mark.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mark.llm.LlmRequest;
import dev.mark.llm.LlmResponse;
import dev.mark.llm.LlmProvider;
import dev.mark.llm.PlanResponse;
import dev.mark.llm.LlmToolCall;
import dev.mark.task.TaskRequest;
import dev.mark.tool.Tool;
import dev.mark.tool.ToolRegistry;
import dev.mark.tool.ToolResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentEngineTest {
    @Test void completesAThreeToolMultiStepTaskAndPreservesHistory() {
        LlmProvider router = mock(LlmProvider.class);
        when(router.plan(any())).thenReturn(new PlanResponse(List.of("step 1", "step 2")));
        when(router.complete(any())).thenReturn(
                toolCall("increment", Map.of("value", 1)),
                toolCall("increment", Map.of("value", 2)),
                toolCall("finish", Map.of()),
                completion("Reached 3 and finished."));

        AgentEngine engine = engine(router, 10, new IncrementTool(), new FinishTool());
        TaskRequest request = new TaskRequest("Start at 1 and finish at 3", null);
        AgentState state = new AgentState(java.util.UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        engine.executeTask(state, request);

        assertEquals(AgentStatus.COMPLETED, state.status());
        verify(router, times(4)).complete(any());
        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(router, times(4)).complete(requests.capture());
        assertEquals(0, requests.getAllValues().getFirst().history().size());
        assertEquals(4, requests.getAllValues().get(2).history().size());
        assertEquals("increment", requests.getAllValues().get(2).history().get(0).toolCalls().getFirst().name());
    }

    @Test void truncatesHistoryIfExceedsMaxHistoryLength() {
        LlmProvider router = mock(LlmProvider.class);
        when(router.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(router.complete(any())).thenReturn(
                toolCall("increment", Map.of("value", 1)),
                toolCall("increment", Map.of("value", 2)),
                toolCall("increment", Map.of("value", 3)),
                toolCall("increment", Map.of("value", 4)),
                toolCall("increment", Map.of("value", 5)),
                toolCall("finish", Map.of()),
                completion("Finished.")
        );

        // maxHistoryLength is 6, we have 6 tool calls which means 12 history entries (tool + observation for each)
        AgentEngine engine = engine(router, 10, new IncrementTool(), new FinishTool());
        TaskRequest request = new TaskRequest("increment lots", null);
        AgentState state = new AgentState(java.util.UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        engine.executeTask(state, request);

        assertEquals(AgentStatus.COMPLETED, state.status());
        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(router, times(7)).complete(requests.capture());
        
        // After step 1 (0 history entries passed initially)
        assertEquals(0, requests.getAllValues().get(0).history().size());
        
        // On the 7th call, normally we would have 12 entries, but max is 6
        assertEquals(6, requests.getAllValues().get(6).history().size());
    }

    @Test void failsWhenMaximumStepsAreExceeded() {
        LlmProvider router = mock(LlmProvider.class);
        when(router.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(router.complete(any())).thenReturn(toolCall("increment", Map.of("value", 1)), toolCall("increment", Map.of("value", 2)));

        AgentEngine engine = engine(router, 2, new IncrementTool());
        TaskRequest request = new TaskRequest("keep incrementing", null);
        AgentState state = new AgentState(java.util.UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        engine.executeTask(state, request);

        assertEquals(AgentStatus.FAILED, state.status());
        assertTrue(state.steps().getLast().outcome().contains("Maximum agent steps exceeded"));
    }

    @Test void returnsToolFailureToLlmAndAllowsSuccessfulRecovery() {
        Tool unstable = new Tool() {
            public String name() { return "unstable"; }
            public ToolResult execute(dev.mark.tool.ToolRequest request) { return new ToolResult(false, "temporary failure", Map.of()); }
        };
        LlmProvider router = mock(LlmProvider.class);
        when(router.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(router.complete(any())).thenReturn(toolCall("unstable", Map.of()), toolCall("finish", Map.of()), completion("Recovered."));

        AgentEngine engine = engine(router, 10, unstable, new FinishTool());
        TaskRequest request = new TaskRequest("recover", null);
        AgentState state = new AgentState(java.util.UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        engine.executeTask(state, request);

        assertEquals(AgentStatus.COMPLETED, state.status());
        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(router, times(3)).complete(requests.capture());
        assertEquals("temporary failure", requests.getAllValues().get(1).history().getLast().toolObservation().observation());
    }

    @Test void recordsUnknownToolAndAllowsReplanning() {
        LlmProvider router = mock(LlmProvider.class);
        when(router.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(router.complete(any())).thenReturn(toolCall("unknown", Map.of()), toolCall("finish", Map.of()), completion("Recovered from unknown tool."));

        AgentEngine engine = engine(router, 10, new FinishTool());
        TaskRequest request = new TaskRequest("recover", null);
        AgentState state = new AgentState(java.util.UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        engine.executeTask(state, request);

        assertEquals(AgentStatus.COMPLETED, state.status());
        assertTrue(state.steps().stream().anyMatch(step -> step.outcome().contains("Unknown tool")));
    }

    @Test void failsCleanlyForMalformedResponseOrUnavailableLlm() {
        LlmProvider malformed = mock(LlmProvider.class);
        when(malformed.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(malformed.complete(any())).thenReturn(new LlmResponse("", "local", false, List.of()));
        AgentEngine badEngine = engine(malformed, 10, new FinishTool());
        AgentState badState = new AgentState(java.util.UUID.randomUUID(), "bad response");
        badState.transitionTo(AgentStatus.PLANNING);
        badEngine.executeTask(badState, new TaskRequest("bad response", null));
        assertEquals(AgentStatus.FAILED, badState.status());

        LlmProvider unavailable = mock(LlmProvider.class);
        when(unavailable.plan(any())).thenReturn(new PlanResponse(List.of("step 1")));
        when(unavailable.complete(any())).thenThrow(new RuntimeException("llama.cpp unavailable"));
        AgentEngine unavailEngine = engine(unavailable, 10, new FinishTool());
        AgentState unavailState = new AgentState(java.util.UUID.randomUUID(), "unavailable");
        unavailState.transitionTo(AgentStatus.PLANNING);
        unavailEngine.executeTask(unavailState, new TaskRequest("unavailable", null));
        assertEquals(AgentStatus.FAILED, unavailState.status());
    }

    private AgentEngine engine(LlmProvider router, int maxSteps, Tool... tools) {
        return new AgentEngine(new ToolRegistry(List.of(tools)), router, new CompletionVerifier(), new AgentProperties(maxSteps, 6, 20000), mock(dev.mark.task.data.TaskRepository.class));
    }

    private LlmResponse toolCall(String name, Map<String, Object> arguments) {
        return new LlmResponse("", "local", false, List.of(new LlmToolCall("call_" + name, name, arguments)));
    }

    private LlmResponse completion(String content) {
        return new LlmResponse(content, "local", false, List.of());
    }
}
