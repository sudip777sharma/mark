package dev.mark.task;

import dev.mark.agent.AgentEngine;
import dev.mark.agent.AgentStep;
import dev.mark.task.data.TaskEntity;
import dev.mark.task.data.TaskRepository;
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

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final AgentEngine agentEngine;
    private final TaskRepository taskRepository;

    public TaskController(AgentEngine agentEngine, TaskRepository taskRepository) {
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TaskResponse createAndRun(@Valid @RequestBody TaskRequest request) { 
        return agentEngine.submit(request); 
    }

    @GetMapping
    public List<TaskResponse> listTasks() {
        return taskRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::mapToResponse).toList();
    }

    @GetMapping("/{taskId}")
    public TaskResponse getTask(@PathVariable UUID taskId) {
        return taskRepository.findById(taskId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    @PostMapping("/{taskId}/reply")
    public void replyToTask(@PathVariable UUID taskId, @RequestBody String reply) {
        java.util.concurrent.CompletableFuture<String> future = AgentEngine.PENDING_REPLIES.get(taskId);
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

    private TaskResponse mapToResponse(TaskEntity entity) {
        List<AgentStep> steps = entity.getSteps().stream()
                .map(s -> new AgentStep(s.getStepNumber(), s.getDescription(), s.getToolName(), s.getOutcome(), s.getProvider()))
                .toList();
        return new TaskResponse(entity.getId(), entity.getGoal(), entity.getStatus(), entity.getFinalAnswer(), entity.getPlan(), steps);
    }
}
