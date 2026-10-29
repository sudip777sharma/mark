package dev.mark.task.controller;

import dev.mark.agent.model.AgentStepModel;
import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.task.dto.TaskRequestDTO;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.LlmInteractionLogEntity;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.LlmInteractionLogRepository;
import dev.mark.task.repository.TaskRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    private final AgentOrchestratorService agentEngine;
    private final TaskRepository taskRepository;
    private final java.util.concurrent.Executor agentTaskExecutor;
    private final LlmInteractionLogRepository llmLogRepository;

    public TaskController(AgentOrchestratorService agentEngine, TaskRepository taskRepository, @org.springframework.beans.factory.annotation.Qualifier("agentTaskExecutor") java.util.concurrent.Executor agentTaskExecutor, LlmInteractionLogRepository llmLogRepository) {
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
        this.agentTaskExecutor = agentTaskExecutor;
        this.llmLogRepository = llmLogRepository;
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
    public List<TaskResponseDTO> listAll() {
        return taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @GetMapping("/{taskId}")
    public TaskResponseDTO getTask(@PathVariable UUID taskId) {
        return taskRepository.findById(taskId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: " + taskId));
    }

    @GetMapping("/{taskId}/llm-logs")
    public List<LlmInteractionLogEntity> getLlmLogs(@PathVariable UUID taskId) {
        return llmLogRepository.findByTaskIdOrderByCreatedAtAsc(taskId.toString());
    }

    private TaskResponseDTO mapToResponse(TaskEntity entity) {
        List<AgentStepModel> steps = entity.getSteps().stream()
                .map(s -> new AgentStepModel(s.getStepNumber(), s.getDescription(), s.getToolName(), s.getToolArguments(), s.getOutcome(), s.getProvider(), s.getTimestamp()))
                .toList();
        return new TaskResponseDTO(entity.getId(), entity.getGoal(), entity.getStatus(), entity.getFinalAnswer(), entity.getCurrentAction(), entity.getPlan(), entity.getLlmRequestCount(), steps);
    }
}
