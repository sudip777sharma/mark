package dev.mark.task;

import dev.mark.agent.AgentStatus;
import dev.mark.task.data.TaskEntity;
import dev.mark.task.data.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        List<AgentStatus> activeStatuses = List.of(
                AgentStatus.CREATED,
                AgentStatus.PLANNING,
                AgentStatus.EXECUTING,
                AgentStatus.VERIFYING
        );

        List<TaskEntity> zombieTasks = taskRepository.findByStatusIn(activeStatuses);
        
        if (!zombieTasks.isEmpty()) {
            log.info("Found {} zombie tasks. Marking them as FAILED.", zombieTasks.size());
            for (TaskEntity task : zombieTasks) {
                task.setStatus(AgentStatus.FAILED);
                task.setFinalAnswer("Task interrupted by server restart.");
                log.info("Task {} marked as FAILED.", task.getId());
            }
            taskRepository.saveAll(zombieTasks);
        } else {
            log.info("No zombie tasks found on startup.");
        }
    }
}
