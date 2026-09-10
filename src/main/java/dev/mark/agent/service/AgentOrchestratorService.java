package dev.mark.agent.service;



import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.*;
import dev.mark.llm.dto.*;
import dev.mark.agent.config.AgentPropertiesConfig;
import dev.mark.agent.model.*;
import dev.mark.agent.observer.AgentEnvironmentObserver;
import dev.mark.tool.registry.ToolRegistry;
import dev.mark.llm.exception.LlmProviderException;
import dev.mark.llm.service.LlmRouterService;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.AgentStepEmbeddableEntity;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
* **What the class does**:
* Coordinates the lifecycle of autonomous agent tasks, managing planning, step-by-step execution, tool invocations, environment observation, and completion verification.
*
* **Why it is useful**:
* Centralizes complex agentic workflows, abstracting interactions with LLMs, tool registries, and persistence layers into a robust, fault-tolerant orchestrator.
*
* **How it fits in the flow of the application**:
* Acts as the primary service layer orchestrating user requests from tasks, querying LLM routers and tool executors, and persisting state changes via repositories.
*
* **Its methods and variables and how they are useful**:
* - `pendingReplies`: Map tracking asynchronous reply futures by task ID.
* - `executeTask(String, String)`: Initiates and oversees the main agent loop from planning to completion or failure.
* - `generatePlan(String)`: Interacts with the LLM router to break the primary goal into sequential steps.
* - `executeNextStep(AgentStateModel)`: Evaluates current world states, builds execution contexts, invokes tools, and verifies task progress.
* - Supporting helper methods: Manage state persistence, tool definitions, error handling, and prompt context building.
*
* **Its logic and how it gets fit into the overall application logic**:
* Implements a state-driven iterative execution loop (PLANNING -> EXECUTING -> COMPLETED/FAILED) that continuously observes environment changes, executes tool calls, validates outputs, and saves progress to the database.
*/

@Service
public class AgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    private final Map<UUID, CompletableFuture<String>> pendingReplies = new ConcurrentHashMap<>();

    private final LlmRouterService llmRouter;
    private final TaskRepository taskRepository;
    private final AgentEnvironmentObserver environmentObserver;
    private final AgentPropertiesConfig properties;
    private final AgentCompletionVerifierService completionVerifier;
    private final AgentPromptBuilderService promptBuilder;
    private final AgentEnvironmentDeltaService deltaService;
    private final AgentToolExecutorService toolExecutor;
    private final ToolRegistry toolRegistry;

    public AgentOrchestratorService(LlmRouterService llmRouter,
                                    TaskRepository taskRepository,
                                    AgentEnvironmentObserver environmentObserver,
                                    AgentPropertiesConfig properties,
                                    AgentCompletionVerifierService completionVerifier,
                                    AgentPromptBuilderService promptBuilder,
                                    AgentEnvironmentDeltaService deltaService,
                                    AgentToolExecutorService toolExecutor,
                                    ToolRegistry toolRegistry) {
        this.llmRouter = llmRouter;
        this.taskRepository = taskRepository;
        this.environmentObserver = environmentObserver;
        this.properties = properties;
        this.completionVerifier = completionVerifier;
        this.promptBuilder = promptBuilder;
        this.deltaService = deltaService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
    }

    public Map<UUID, CompletableFuture<String>> getPendingReplies() {
        return pendingReplies;
    }

    public TaskResponseDTO executeTask(String taskId, String goal) {
        log.info("event=task_started taskId={}", taskId);

        AgentStateModel state = new AgentStateModel(taskId, goal);
        state.setStatus("PLANNING");

        try {
            log.info(">>> [ORCHESTRATOR:PLAN_START] taskId={} goal='{}'", taskId, goal);
            List<String> plan = generatePlan(goal);
            log.info("<<< [ORCHESTRATOR:PLAN_READY] taskId={} stepsCount={}", taskId, plan.size());
            state.setPlan(plan);
            state.setStatus("EXECUTING");
            saveState(state);

            while ("EXECUTING".equals(state.status())) {
                executeNextStep(state);
            }

        } catch (Exception e) {
            log.error("!!! [ORCHESTRATOR:FAILED] taskId={} error={}", taskId, e.getMessage(), e);
            return handleFailure(state, failureMessage(e));
        }

        log.info("=== [ORCHESTRATOR:FINISHED] taskId={} status={}", taskId, state.status());
        return response(state);
    }

    private List<String> generatePlan(String goal) {
        LlmRequestDTO request = new LlmRequestDTO(
                null,
                promptBuilder.systemPromptForPlanning(),
                null,
                List.of(),
                null,
                List.of(LlmMessageDTO.userMessage(goal))
        );

        PlanResponseDTO planResponse = llmRouter.plan(request);
        if (planResponse.steps() == null || planResponse.steps().isEmpty()) {
            log.warn("--- [ORCHESTRATOR:PLAN_FALLBACK] taskId=unknown goal='{}' using single step fallback", goal);
            return List.of(goal); // fallback to goal as single step
        }
        return planResponse.steps();
    }

    private void executeNextStep(AgentStateModel state) {
        if (state.steps().size() >= properties.maxSteps()) {
            log.warn("!!! [ORCHESTRATOR:MAX_STEPS] taskId={} reached max steps limit={}", state.taskId(), properties.maxSteps());
            handleFailure(state, "Max steps reached (" + properties.maxSteps() + ")");
            return;
        }

        int stepNumber = state.steps().size() + 1;
        log.info(">>> [ORCHESTRATOR:STEP_START] taskId={} step={}/{}", state.taskId(), stepNumber, properties.maxSteps());

        AgentWorldStateModel currentWorldState = environmentObserver.observe();
        AgentWorldStateDeltaModel stateDelta = currentWorldState.compareTo(state.latestWorldState());
        state.updateWorldState(currentWorldState);

        String systemPrompt = promptBuilder.systemPromptForExecution(state.plan(), currentWorldState, deltaService.formatWorldStateDelta(stateDelta));

        List<LlmMessageDTO> context = buildContext(state);
        LlmRequestDTO request = new LlmRequestDTO(
                null,
                systemPrompt,
                "Continue execution.",
                toolRegistryDefinitions(),
                "auto",
                context
        );

        try {
            LlmResponseDTO response = llmRouter.complete(request);

            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                LlmToolCallDTO toolCall = response.toolCalls().get(0);

                LlmMessageDTO assistantMessage = LlmMessageDTO.assistantToolCall(toolCall);
                state.addMessage(assistantMessage);

                log.info("--- [ORCHESTRATOR:CALLING_TOOL] taskId={} step={} tool={}", state.taskId(), stepNumber, toolCall.name());
                ToolResultDTO toolResult = toolExecutor.execute(state.taskId(), toolCall);

                String outcome = toolResult.successful() ? "Success" : "Failed";
                String details = toolResult.observation() + (toolResult.metadata() != null && !toolResult.metadata().isEmpty() ? "\nData: " + toolResult.metadata() : "");

                LlmToolObservationDTO observation = new LlmToolObservationDTO(toolCall.id(), toolCall.name(), toolResult.successful(), details, toolResult.metadata());
                LlmMessageDTO toolMessage = LlmMessageDTO.toolObservation(observation);
                state.addMessage(toolMessage);

                state.addStep(new AgentStepModel(stepNumber, response.content(), toolCall.name(), outcome + ": " + details, response.provider()));
                saveState(state);

            } else {
                // No tools called, agent believes it is done.
                log.info("--- [ORCHESTRATOR:NO_TOOLS] taskId={} step={} agent returned direct completion", state.taskId(), stepNumber);
                state.addMessage(LlmMessageDTO.assistantCompletion(response.content()));

                AgentVerificationResultModel verification = completionVerifier.verify(state.goal(), state.messages(), currentWorldState);
                if (verification.verified()) {
                    log.info("=== [ORCHESTRATOR:VERIFIED] taskId={} step={} Task Complete! reason='{}'",
                        state.taskId(), stepNumber, verification.reason());
                    state.setStatus("COMPLETED");
                    state.setFinalAnswer(response.content());
                    state.addStep(new AgentStepModel(stepNumber, "Task Verified Complete", "None", verification.reason(), response.provider()));
                    saveState(state);
                } else {
                    log.warn("=== [ORCHESTRATOR:CRITIQUE] taskId={} step={} Verification Failed! reason='{}' -> looping back",
                        state.taskId(), stepNumber, verification.reason());
                    LlmMessageDTO critiqueMessage = LlmMessageDTO.userMessage("System Critique: Task is not complete. " + verification.reason());
                    state.addMessage(critiqueMessage);
                    state.addStep(new AgentStepModel(stepNumber, response.content(), "Verification Failed", verification.reason(), response.provider()));
                    saveState(state);
                }
            }

        } catch (LlmProviderException e) {
            log.error("!!! [ORCHESTRATOR:LLM_ERROR] taskId={} step={} error={}", state.taskId(), stepNumber, e.getMessage());
            handleFailure(state, "LLM Provider Error: " + e.getMessage());
        }
    }

    private List<LlmToolDefinitionDTO> toolRegistryDefinitions() {
        return toolRegistry.availableTools().stream()
                .map(tool -> new LlmToolDefinitionDTO(tool.name(), tool.description(), tool.parameterSchema()))
                .collect(Collectors.toList());
    }

    private List<LlmMessageDTO> buildContext(AgentStateModel state) {
        List<LlmMessageDTO> context = new ArrayList<>();
        context.add(LlmMessageDTO.userMessage("Task: " + state.goal()));
        context.addAll(promptBuilder.compactHistory(state.messages(), properties));
        return context;
    }

    private TaskResponseDTO handleFailure(AgentStateModel state, String reason) {
        state.setStatus("FAILED");
        int step = state.steps().size() + 1;
        state.addStep(new AgentStepModel(step, "Agent execution failed", "", reason, "system"));
        log.warn("event=task_failed taskId={} reason={}", state.taskId(), reason);
        return response(state);
    }

    private TaskResponseDTO response(AgentStateModel state) {
        saveState(state);
        return new TaskResponseDTO(java.util.UUID.fromString(state.taskId()), state.goal(), dev.mark.agent.model.AgentStatusModel.valueOf(state.status()), state.finalAnswer(), state.plan(), state.steps());
    }

    private void saveState(AgentStateModel state) {
        TaskEntity entity = new TaskEntity(java.util.UUID.fromString(state.taskId()), state.goal(), dev.mark.agent.model.AgentStatusModel.valueOf(state.status()), state.finalAnswer(), state.createdAt());
        entity.setPlan(state.plan());
        List<AgentStepEmbeddableEntity> embeddables = state.steps().stream()
                .map(s -> new AgentStepEmbeddableEntity(s.number(), s.description(), s.toolName(), s.outcome(), s.provider()))
                .collect(Collectors.toList());
        entity.setSteps(embeddables);
        taskRepository.save(entity);
    }

    private String failureMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
