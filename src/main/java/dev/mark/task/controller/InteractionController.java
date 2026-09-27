package dev.mark.task.controller;

import dev.mark.agent.dto.InteractionRequestDTO;
import dev.mark.agent.dto.InteractionResponseDTO;
import dev.mark.agent.model.AgentStatusModel;
import dev.mark.agent.service.AgentIntentRouterService;
import dev.mark.agent.service.AgentOrchestratorService;
import dev.mark.task.service.TaskSseService;
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
    private final TaskSseService taskSseService;
    private final java.util.concurrent.Executor agentTaskExecutor;

    public InteractionController(
            AgentIntentRouterService intentRouter,
            AgentOrchestratorService agentEngine,
            TaskRepository taskRepository,
            TaskSseService taskSseService,
            @org.springframework.beans.factory.annotation.Qualifier("agentTaskExecutor") java.util.concurrent.Executor agentTaskExecutor) {
        this.intentRouter = intentRouter;
        this.agentEngine = agentEngine;
        this.taskRepository = taskRepository;
        this.taskSseService = taskSseService;
        this.agentTaskExecutor = agentTaskExecutor;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public InteractionResponseDTO interact(@Valid @RequestBody InteractionRequestDTO request) {
        String userInput = request.input().trim();
        UUID taskId = UUID.randomUUID();
        
        log.info(">>> [INTERACTION:RECEIVED] taskId={} input='{}'", taskId, userInput);

        // 1. Instantly create and save a task in CREATED state to acknowledge receipt
        TaskEntity entity = new TaskEntity(taskId, userInput, AgentStatusModel.CREATED, null, Instant.now());
        entity.setConfigId(request.configId());
        entity.setCurrentAction("Analyzing intent and classifying goal...");
        taskRepository.save(entity);
        taskSseService.broadcastTaskUpdate(entity);

        // 2. Dispatch routing and execution to the background thread pool
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("Background intent classification starting for task {}", taskId);
                InteractionResponseDTO routing = intentRouter.classifyAndRoute(userInput);
                
                TaskEntity t = taskRepository.findById(taskId).orElse(null);
                if (t == null) return;

                if (routing.intent() == dev.mark.agent.model.AgentIntentType.AUTONOMOUS_TASK) {
                    t.setStatus(AgentStatusModel.PLANNING);
                    t.setCurrentAction("Intent classified as Autonomous Task. Generating execution plan...");
                    taskRepository.save(t);
                    taskSseService.broadcastTaskUpdate(t);
                    
                    // Proceed with autonomous execution loop
                    agentEngine.executeTask(taskId.toString(), userInput);
                } else {
                    // Fast-track simple chats and QA directly to completion
                    t.setStatus(AgentStatusModel.COMPLETED);
                    t.setFinalAnswer(routing.reply());
                    t.setCurrentAction("Intent classified as " + routing.intent() + ". Answered directly.");
                    taskRepository.save(t);
                    taskSseService.broadcastTaskUpdate(t);
                    log.info("<<< [INTERACTION:FAST_TRACK] taskId={} intent={} replyLength={}", taskId, routing.intent(), routing.reply().length());
                }
            } catch (Exception e) {
                log.error("Error during async interaction routing for task {}", taskId, e);
                taskRepository.findById(taskId).ifPresent(t -> {
                    t.setStatus(AgentStatusModel.FAILED);
                    t.setFinalAnswer("Internal error during intent classification.");
                    taskRepository.save(t);
                    taskSseService.broadcastTaskUpdate(t);
                });
            }
        }, agentTaskExecutor);

        // 3. Return instantly so the frontend UI doesn't lag
        return InteractionResponseDTO.task(taskId.toString(), "Request received. Analyzing intent in background...");
    }
}
