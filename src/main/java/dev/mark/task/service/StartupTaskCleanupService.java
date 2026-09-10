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
 * **What it does:**
 * Cleans up "zombie" tasks that were left in active states when the application shut down or crashed.
 *
 * **Why it is useful:**
 * Prevents interrupted tasks from remaining permanently stuck in active states, ensuring database consistency and accurate task tracking.
 *
 * **Application Flow Integration:**
 * Runs automatically during the application startup phase, triggered immediately after the Spring Boot application context is fully initialized.
 *
 * **Methods and Variables:**
 * - `taskRepository`: Interacts with the database to fetch and update task records.
 * - `log`: Records the cleanup progress and details of modified tasks.
 * - `cleanupZombieTasks()`: The transactional entry point that executes the cleanup process.
 *
 * **Logic and System Integration:**
 * - Listens for `ApplicationReadyEvent`.
 * - Queries tasks with active statuses: CREATED, PLANNING, EXECUTING, and VERIFYING.
 * - Updates their status to FAILED and sets the final answer to "Task interrupted by server restart."
 * - Saves the updated tasks, resetting the system state for clean subsequent operations.
 */

@Service
public class StartupTaskCleanupService {

    private static final Logger log = LoggerFactory.getLogger(StartupTaskCleanupService.class);
    private final TaskRepository taskRepository;

    public StartupTaskCleanupService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupZombieTasks() {
        List<AgentStatusModel> activeStatuses = List.of(
                AgentStatusModel.CREATED,
                AgentStatusModel.PLANNING,
                AgentStatusModel.EXECUTING,
                AgentStatusModel.VERIFYING
        );

        List<TaskEntity> zombieTasks = taskRepository.findByStatusIn(activeStatuses);

        if (!zombieTasks.isEmpty()) {
            log.info("Found {} zombie tasks. Marking them as FAILED.", zombieTasks.size());
            for (TaskEntity task : zombieTasks) {
                task.setStatus(AgentStatusModel.FAILED);
                task.setFinalAnswer("Task interrupted by server restart.");
                log.info("Task {} marked as FAILED.", task.getId());
            }
            taskRepository.saveAll(zombieTasks);
        } else {
            log.info("No zombie tasks found on startup.");
        }
    }
}
