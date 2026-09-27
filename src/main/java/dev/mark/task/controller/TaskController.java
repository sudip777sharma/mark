package dev.mark.task.controller;

import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.agent.model.AgentStepModel;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.dto.TaskRequestDTO;
import dev.mark.task.dto.TaskResponseDTO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Acts as the primary REST controller managing task lifecycle endpoints, including creation, retrieval, and interactive replies or approvals.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final AgentOrchestratorService agentEngine;
    private final TaskRepository taskRepository;
    private final java.util.concurrent.Executor agentTaskExecutor;

    public TaskController(AgentOrchestratorService agentEngine, TaskRepository taskRepository, @org.springframework.beans.factory.annotation.Qualifier("agentTaskExecutor") java.util.concurrent.Executor agentTaskExecutor) {
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskResponseDTO createAndRun(@Valid @RequestBody TaskRequestDTO request) {
        UUID taskId = UUID.randomUUID();
        TaskEntity entity = new TaskEntity(taskId, request.goal(), dev.mark.agent.model.AgentStatusModel.PLANNING, null, java.time.Instant.now());
        entity.setConfigId(request.configId());
        taskRepository.save(entity);

        log.info("Dispatching task {} to dedicated agent thread pool", taskId);
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            log.debug("Agent orchestrator starting execution for task {}", taskId);
            agentEngine.executeTask(taskId.toString(), request.goal());
        }, agentTaskExecutor);

        return mapToResponse(entity);
    }

    @GetMapping
    public List<TaskResponseDTO> listTasks() {
        return taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::mapToResponse).toList();
    }

    @GetMapping("/{taskId}")
    public TaskResponseDTO getTask(@PathVariable UUID taskId) {
        return taskRepository.findById(taskId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    @PostMapping("/{taskId}/reply")
    public void replyToTask(@PathVariable UUID taskId, @RequestBody String reply) {
        java.util.concurrent.CompletableFuture<String> future = agentEngine.getPendingReplies().get(taskId);
        if (future != null) {
            future.complete(reply);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task is not waiting for a reply");
        }
    }

    @PostMapping("/{taskId}/approve")
    public void approveTask(@PathVariable UUID taskId) {
        replyToTask(taskId, "approve");
    }

    private TaskResponseDTO mapToResponse(TaskEntity entity) {
        List<AgentStepModel> steps = entity.getSteps().stream()
                .map(s -> new AgentStepModel(s.getStepNumber(), s.getDescription(), s.getToolName(), s.getToolArguments(), s.getOutcome(), s.getProvider(), s.getTimestamp()))
                .toList();
        return new TaskResponseDTO(entity.getId(), entity.getGoal(), entity.getStatus(), entity.getFinalAnswer(), entity.getCurrentAction(), entity.getPlan(), entity.getLlmRequestCount(), steps);
    }
}

