package dev.mark.task.controller;

import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.agent.model.AgentStepModel;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.dto.TaskRequestDTO;
import dev.mark.task.dto.TaskResponseDTO;
import jakarta.validation.Valid;
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
- What the class does: Acts as the primary REST controller managing task lifecycle endpoints, including creation, retrieval, and interactive replies or approvals.
- Why it is useful: Exposes HTTP APIs that allow external clients to submit goals, monitor task execution progress, and interact with waiting agent steps.
- How it fits in the flow of the application: Serves as the entry point for client requests, bridging the presentation layer with persistence and core agent orchestration services.
- Its methods and variables and how they are useful:
  - agentEngine: Orchestrates asynchronous background execution of AI agent tasks and manages pending human-in-the-loop interactions.
  - taskRepository: Handles database persistence and retrieval of task entities.
  - createAndRun: Initializes a new task entity, triggers asynchronous agent execution, and returns a response.
  - listTasks and getTask: Fetch stored tasks for status monitoring.
  - replyToTask and approveTask: Resume paused tasks by supplying human inputs to pending execution threads.
  - mapToResponse: Translates internal task entities into client-safe DTO representations.
- Its logic and how it gets fit into the overall application logic: Accepts incoming payloads, persists initial states, delegates heavy lifting to asynchronous agent workflows, and exposes query and control hooks for real-time task management.
*/

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final AgentOrchestratorService agentEngine;
    private final TaskRepository taskRepository;

    public TaskController(AgentOrchestratorService agentEngine, TaskRepository taskRepository) {
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskResponseDTO createAndRun(@Valid @RequestBody TaskRequestDTO request) {
        UUID taskId = UUID.randomUUID();
        TaskEntity entity = new TaskEntity(taskId, request.goal(), dev.mark.agent.model.AgentStatusModel.PLANNING, null, java.time.Instant.now());
        taskRepository.save(entity);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            agentEngine.executeTask(taskId.toString(), request.goal());
        });

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
                .map(s -> new AgentStepModel(s.getStepNumber(), s.getDescription(), s.getToolName(), s.getOutcome(), s.getProvider()))
                .toList();
        return new TaskResponseDTO(entity.getId(), entity.getGoal(), entity.getStatus(), entity.getFinalAnswer(), entity.getPlan(), steps);
    }
}
