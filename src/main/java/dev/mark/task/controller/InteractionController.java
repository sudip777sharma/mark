package dev.mark.task.controller;

import dev.mark.agent.dto.InteractionRequestDTO;
import dev.mark.agent.dto.InteractionResponseDTO;
import dev.mark.agent.model.AgentStatusModel;
import dev.mark.agent.service.AgentIntentRouterService;
import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller handling unified user interactions across text and voice.
 *
 * Uses the Dynamic Intent Router to route casual chat, direct informational Q&A,
 * and autonomous multi-step tasks to their appropriate lifecycles.
 */
@RestController
@RequestMapping("/api/interact")
public class InteractionController {

    private static final Logger log = LoggerFactory.getLogger(InteractionController.class);

    private final AgentIntentRouterService intentRouter;
    private final AgentOrchestratorService agentEngine;
    private final TaskRepository taskRepository;

    public InteractionController(
            AgentIntentRouterService intentRouter,
            AgentOrchestratorService agentEngine,
            TaskRepository taskRepository) {
        this.intentRouter = intentRouter;
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public InteractionResponseDTO interact(@Valid @RequestBody InteractionRequestDTO request) {
        String userInput = request.input().trim();

        InteractionResponseDTO routing = intentRouter.classifyAndRoute(userInput);

        if (routing.intent() == dev.mark.agent.model.AgentIntentType.AUTONOMOUS_TASK) {
            UUID taskId = UUID.randomUUID();
            log.info(">>> [INTERACTION:TASK_LAUNCH] taskId={} goal='{}'", taskId, userInput);

            TaskEntity entity = new TaskEntity(taskId, userInput, AgentStatusModel.PLANNING, null, Instant.now());
            taskRepository.save(entity);

            CompletableFuture.runAsync(() -> {
                agentEngine.executeTask(taskId.toString(), userInput);
            });

            return InteractionResponseDTO.task(taskId.toString(), "Task initialized. Generating execution plan...");
        }

        log.info("<<< [INTERACTION:DIRECT_REPLY] intent={} replyLength={}", routing.intent(), routing.reply().length());
        return routing;
    }
}
