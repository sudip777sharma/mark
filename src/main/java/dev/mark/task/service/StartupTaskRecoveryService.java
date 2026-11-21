package dev.mark.task.service;

import dev.mark.agent.model.AgentStatusModel;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Intelligent startup recovery and reconciliation service for MARK.
 * Replaces naive blanket failure marking with durable checkpoint preservation,
 * classifying interrupted tasks and keeping them ready for safe inspection or resumption.
 */
@Service
public class StartupTaskRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(StartupTaskRecoveryService.class);
    private final TaskRepository taskRepository;

    public StartupTaskRecoveryService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void reconcileInterruptedTasks() {
        List<AgentStatusModel> activeStatuses = List.of(
                AgentStatusModel.CREATED,
                AgentStatusModel.PLANNING,
                AgentStatusModel.EXECUTING,
                AgentStatusModel.VERIFYING,
                AgentStatusModel.COOLDOWN,
                AgentStatusModel.RECOVERING
        );

        List<TaskEntity> interruptedTasks = taskRepository.findByStatusIn(activeStatuses);

        if (!interruptedTasks.isEmpty()) {
            log.info(">>> [STARTUP:RECOVERY] Found {} interrupted tasks requiring checkpoint reconciliation.", interruptedTasks.size());
            for (TaskEntity task : interruptedTasks) {
                int completedSteps = task.getSteps() != null ? task.getSteps().size() : 0;
                task.setStatus(AgentStatusModel.INTERRUPTED);

                String recoverySummary = String.format(
                        "Task execution was interrupted by system shutdown/restart. Preserved %d completed steps and plan checkpoint for resumption.",
                        completedSteps
                );
                task.setFinalAnswer(recoverySummary);
                task.setCurrentAction("Interrupted at step " + completedSteps + " (preserved checkpoint)");

                log.info("<<< [STARTUP:RECOVERY:PRESERVED] taskId={} completedSteps={} status={}",
                        task.getId(), completedSteps, task.getStatus());
            }
            taskRepository.saveAll(interruptedTasks);
        } else {
            log.info("<<< [STARTUP:RECOVERY] No interrupted tasks detected on startup.");
        }
    }
}
