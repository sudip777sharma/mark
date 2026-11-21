package dev.mark.task.service;

import dev.mark.agent.model.AgentStatusModel;
import dev.mark.task.entity.AgentStepEmbeddableEntity;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StartupTaskRecoveryServiceTest {

    @Test
    void reconcilesInterruptedTasksPreservingCheckpoint() {
        TaskRepository repository = mock(TaskRepository.class);
        StartupTaskRecoveryService service = new StartupTaskRecoveryService(repository);

        TaskEntity task = new TaskEntity(
                UUID.randomUUID(),
                "Create reports and send notifications",
                AgentStatusModel.EXECUTING,
                null,
                Instant.now()
        );
        task.setSteps(List.of(
                new AgentStepEmbeddableEntity(1, "Step 1", "write_file", "{}", "Success", "openai", Instant.now()),
                new AgentStepEmbeddableEntity(2, "Step 2", "desktop_automation", "{}", "Success", "openai", Instant.now())
        ));
        task.setPlan(List.of("Step 1", "Step 2", "Step 3"));

        when(repository.findByStatusIn(anyList())).thenReturn(List.of(task));

        service.reconcileInterruptedTasks();

        assertEquals(AgentStatusModel.INTERRUPTED, task.getStatus());
        assertTrue(task.getFinalAnswer().contains("Preserved 2 completed steps"));
        assertTrue(task.getCurrentAction().contains("Interrupted at step 2"));
        verify(repository).saveAll(List.of(task));
    }

    @Test
    void doesNothingWhenNoInterruptedTasksFound() {
        TaskRepository repository = mock(TaskRepository.class);
        StartupTaskRecoveryService service = new StartupTaskRecoveryService(repository);

        when(repository.findByStatusIn(anyList())).thenReturn(List.of());

        service.reconcileInterruptedTasks();

        verify(repository, never()).saveAll(any());
    }
}
