package dev.mark.task.controller;

import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.task.repository.LlmInteractionLogRepository;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.service.TaskSseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

public class TaskControllerTest {

    private AgentOrchestratorService orchestratorService;
    private TaskRepository taskRepository;
    private Executor executor;
    private LlmInteractionLogRepository llmLogRepository;
    private TaskSseService taskSseService;
    private TaskController taskController;

    @BeforeEach
    public void setUp() {
        orchestratorService = mock(AgentOrchestratorService.class);
        taskRepository = mock(TaskRepository.class);
        executor = mock(Executor.class);
        llmLogRepository = mock(LlmInteractionLogRepository.class);
        taskSseService = mock(TaskSseService.class);

        taskController = new TaskController(
                orchestratorService,
                taskRepository,
                executor,
                llmLogRepository,
                taskSseService
        );
    }

    @Test
    public void testStopTaskDelegatesToOrchestrator() {
        UUID taskId = UUID.randomUUID();
        when(orchestratorService.stopTask(taskId)).thenReturn(true);

        taskController.stopTask(taskId);

        verify(orchestratorService, times(1)).stopTask(taskId);
    }
}
