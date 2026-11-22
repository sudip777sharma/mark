package dev.mark.agent.service;

import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.agent.model.AgentStatusModel;
import dev.mark.agent.observer.AgentEnvironmentObserver;
import dev.mark.agent.service.visual.MirageVisualAgentRunner;
import dev.mark.llm.entity.ApiKeyEntry;
import dev.mark.llm.entity.LlmProviderConfig;
import dev.mark.llm.service.LlmRouterService;
import dev.mark.llm.service.LlmSettingsService;
import dev.mark.preference.UserPreferenceService;
import dev.mark.skill.service.L0SkillEngine;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.service.TaskSseService;
import dev.mark.tool.registry.ToolRegistry;
import dev.mark.trajectory.service.TrajectoryRecorderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentOrchestratorServiceTest {

    private UserPreferenceService userPreferenceService;
    private LlmRouterService llmRouter;
    private TaskRepository taskRepository;
    private TaskSseService taskSseService;
    private AgentEnvironmentObserver environmentObserver;
    private AgentPropertiesConfig properties;
    private AgentCompletionVerifierService completionVerifier;
    private AgentPromptBuilderService promptBuilder;
    private AgentEnvironmentDeltaService deltaService;
    private AgentToolExecutorService toolExecutor;
    private ToolRegistry toolRegistry;
    private MeterRegistry meterRegistry;
    private LlmSettingsService llmSettingsService;
    private L0SkillEngine l0SkillEngine;
    private TrajectoryRecorderService trajectoryRecorderService;
    private MirageVisualAgentRunner mirageVisualAgentRunner;

    private AgentOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        userPreferenceService = mock(UserPreferenceService.class);
        llmRouter = mock(LlmRouterService.class);
        taskRepository = mock(TaskRepository.class);
        taskSseService = mock(TaskSseService.class);
        environmentObserver = mock(AgentEnvironmentObserver.class);
        properties = mock(AgentPropertiesConfig.class);
        completionVerifier = mock(AgentCompletionVerifierService.class);
        promptBuilder = mock(AgentPromptBuilderService.class);
        deltaService = mock(AgentEnvironmentDeltaService.class);
        toolExecutor = mock(AgentToolExecutorService.class);
        toolRegistry = mock(ToolRegistry.class);
        meterRegistry = new SimpleMeterRegistry();
        llmSettingsService = mock(LlmSettingsService.class);
        l0SkillEngine = mock(L0SkillEngine.class);
        trajectoryRecorderService = mock(TrajectoryRecorderService.class);
        mirageVisualAgentRunner = mock(MirageVisualAgentRunner.class);

        orchestrator = new AgentOrchestratorService(
                userPreferenceService,
                llmRouter,
                taskRepository,
                taskSseService,
                environmentObserver,
                properties,
                completionVerifier,
                promptBuilder,
                deltaService,
                toolExecutor,
                toolRegistry,
                meterRegistry,
                llmSettingsService,
                l0SkillEngine,
                trajectoryRecorderService,
                mirageVisualAgentRunner
        );
    }

    @Test
    void testExecuteTaskWithMirageConfigDelegatesDirectlyToMirageVisualRunner() {
        UUID taskId = UUID.randomUUID();
        String goal = "Search trains from Delhi to Mumbai on IRCTC";
        Long configId = 99L;

        TaskEntity entity = new TaskEntity(taskId, goal, AgentStatusModel.PLANNING, null, Instant.now());
        entity.setConfigId(configId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(entity));

        LlmProviderConfig mirageConfig = new LlmProviderConfig(
                "Mirage VLM (Local Edge Vision)",
                "mirage",
                "mirage",
                "http://localhost:8088/v1",
                false,
                List.of(new ApiKeyEntry("local", "mirage", true))
        );
        when(llmSettingsService.getConfig(configId)).thenReturn(Optional.of(mirageConfig));

        TaskResponseDTO mirageResponse = new TaskResponseDTO(
                taskId, goal, AgentStatusModel.COMPLETED,
                "Task successfully completed using pure Mirage VLM (22 MB) local execution.",
                "Completed", List.of(), 0, List.of()
        );
        when(mirageVisualAgentRunner.execute(any(), any())).thenReturn(mirageResponse);

        TaskResponseDTO response = orchestrator.executeTask(taskId.toString(), goal);

        assertNotNull(response);
        assertEquals(AgentStatusModel.COMPLETED, response.status());
        assertTrue(response.finalAnswer().contains("Mirage VLM"));

        // Verify delegation directly to Mirage runner
        verify(mirageVisualAgentRunner).execute(any(), any());

        // Verify neither LLM router nor L0 skills were called
        verify(llmRouter, never()).plan(any());
        verify(l0SkillEngine, never()).tryExecute(any(), any());
    }

    @Test
    void testExecuteTaskWithDefaultMirageConfigDelegatesToMirageVisualRunner() {
        UUID taskId = UUID.randomUUID();
        String goal = "Interact with desktop";

        TaskEntity entity = new TaskEntity(taskId, goal, AgentStatusModel.PLANNING, null, Instant.now());
        entity.setConfigId(null); // No explicit configId, relies on default
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(entity));

        LlmProviderConfig defaultMirage = new LlmProviderConfig(
                "Mirage VLM",
                "mirage",
                "mirage",
                "http://localhost:8088/v1",
                true,
                List.of(new ApiKeyEntry("local", "mirage", true))
        );
        when(llmSettingsService.getDefaultConfig()).thenReturn(Optional.of(defaultMirage));

        TaskResponseDTO mirageResponse = new TaskResponseDTO(
                taskId, goal, AgentStatusModel.COMPLETED, "Done via Mirage", "Completed", List.of(), 0, List.of()
        );
        when(mirageVisualAgentRunner.execute(any(), any())).thenReturn(mirageResponse);

        TaskResponseDTO response = orchestrator.executeTask(taskId.toString(), goal);

        assertNotNull(response);
        assertEquals(AgentStatusModel.COMPLETED, response.status());
        verify(mirageVisualAgentRunner).execute(any(), any());
        verify(llmRouter, never()).plan(any());
    }
}
