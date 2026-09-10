package dev.mark.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.mark.llm.dto.LlmToolCallDTO;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.registry.ToolRegistry;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentToolExecutorServiceTest {
    @Test
    void rejectsCallsBlockedByTheSafetyBoundary() {
        ToolRegistry registry = mock(ToolRegistry.class);
        AgentSafetyService safety = mock(AgentSafetyService.class);
        LlmToolCallDTO call = new LlmToolCallDTO("call-1", "execute_command", Map.of("command", "shutdown"));
        when(safety.isDangerousToolCall(call)).thenReturn(true);

        ToolResultDTO result = new AgentToolExecutorService(registry, safety).execute("00000000-0000-0000-0000-000000000001", call);

        assertEquals(false, result.successful());
        assertEquals("Command rejected for safety reasons.", result.observation());
        verify(registry, never()).getTool(any());
    }

    @Test
    void returnsAControlledFailureForAnUnknownTool() {
        ToolRegistry registry = mock(ToolRegistry.class);
        AgentSafetyService safety = mock(AgentSafetyService.class);
        LlmToolCallDTO call = new LlmToolCallDTO("call-2", "unknown", Map.of());
        when(registry.getTool("unknown")).thenReturn(Optional.empty());

        ToolResultDTO result = new AgentToolExecutorService(registry, safety).execute("00000000-0000-0000-0000-000000000002", call);

        assertEquals(false, result.successful());
        assertEquals("Unknown tool: unknown", result.observation());
    }

    @Test
    void delegatesAllowedCallsToTheRegisteredTool() {
        ToolRegistry registry = mock(ToolRegistry.class);
        AgentSafetyService safety = mock(AgentSafetyService.class);
        Tool tool = mock(Tool.class);
        LlmToolCallDTO call = new LlmToolCallDTO("call-3", "echo", Map.of("message", "hello"));
        when(registry.getTool("echo")).thenReturn(Optional.of(tool));
        when(tool.execute(any())).thenReturn(ToolResultDTO.success("hello"));

        ToolResultDTO result = new AgentToolExecutorService(registry, safety).execute("00000000-0000-0000-0000-000000000003", call);

        assertEquals(true, result.successful());
        assertEquals("hello", result.observation());
        verify(tool).execute(any());
    }
}
