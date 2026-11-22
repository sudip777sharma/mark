package dev.mark.agent.service;

import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.agent.model.AgentStatusModel;
import dev.mark.agent.observer.AgentEnvironmentObserver;
import dev.mark.llm.dto.PlanResponseDTO;
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
                trajectoryRecorderService
        );
    }

    @Test
    void testExecuteTaskUsesLlmRouterForPlanning() {
        UUID taskId = UUID.randomUUID();
        String goal = "Search trains from Delhi to Mumbai on IRCTC";
        Long configId = 35L;

        TaskEntity entity = new TaskEntity(taskId, goal, AgentStatusModel.PLANNING, null, Instant.now());
        entity.setConfigId(configId);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(entity));

        LlmProviderConfig groqConfig = new LlmProviderConfig(
                "GROQ config",
                "groq",
                "llama-3.1-70b-versatile",
                "https://api.groq.com/openai/v1",
                true,
                List.of(new ApiKeyEntry("groq", "gsk_test", true))
        );
        when(llmSettingsService.getConfig(configId)).thenReturn(Optional.of(groqConfig));
        when(llmSettingsService.getApiKeysCount(configId)).thenReturn(1);

        PlanResponseDTO planResponse = new PlanResponseDTO(List.of(
                "Open browser and navigate to IRCTC",
                "Use perceive_screen to find train search inputs"
        ));
        when(llmRouter.planStream(any(), any())).thenReturn(planResponse);

        // Cancel task so loop stops after planning
        when(taskRepository.findById(taskId)).thenReturn(
                Optional.of(entity),
                Optional.of(new TaskEntity(taskId, goal, AgentStatusModel.FAILED, "stopped", Instant.now()))
        );

        TaskResponseDTO response = orchestrator.executeTask(taskId.toString(), goal);

        assertNotNull(response);
        // Verify LLM router was called for streaming plan
        verify(llmRouter).planStream(any(), any());
    }
}
