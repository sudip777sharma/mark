package dev.mark.agent.service;

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
import dev.mark.task.service.TaskSseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * **What the class does**:
 * Coordinates the lifecycle of autonomous agent tasks, managing planning,
 * step-by-step execution, tool invocations, environment observation, and
 * completion verification.
 *
 * **Why it is useful**:
 * Centralizes complex agentic workflows, abstracting interactions with LLMs,
 * tool registries, and persistence layers into a robust, fault-tolerant
 * orchestrator.
 *
 * **How it fits in the flow of the application**:
 * Acts as the primary service layer orchestrating user requests from tasks,
 * querying LLM routers and tool executors, and persisting state changes via
 * repositories.
 *
 * **Its methods and variables and how they are useful**:
 * - `pendingReplies`: Map tracking asynchronous reply futures by task ID.
 * - `executeTask(String, String)`: Initiates and oversees the main agent loop
 * from planning to completion or failure.
 * - `generatePlan(String)`: Interacts with the LLM router to break the primary
 * goal into sequential steps.
 * - `executeNextStep(AgentStateModel)`: Evaluates current world states, builds
 * execution contexts, invokes tools, and verifies task progress.
 * - Supporting helper methods: Manage state persistence, tool definitions,
 * error handling, and prompt context building.
 *
 * **Its logic and how it gets fit into the overall application logic**:
 * Implements a state-driven iterative execution loop (PLANNING -> EXECUTING ->
 * COMPLETED/FAILED) that continuously observes environment changes, executes
 * tool calls, validates outputs, and saves progress to the database.
 */

@Service
public class AgentOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestratorService.class);

    private final Map<UUID, CompletableFuture<String>> pendingReplies = new ConcurrentHashMap<>();

    private final LlmRouterService llmRouter;
    private final TaskRepository taskRepository;
    private final TaskSseService taskSseService;
    private final AgentEnvironmentObserver environmentObserver;
    private final AgentPropertiesConfig properties;
    private final dev.mark.preference.UserPreferenceService userPreferenceService;
    private final AgentCompletionVerifierService completionVerifier;
    private final AgentPromptBuilderService promptBuilder;
    private final AgentEnvironmentDeltaService deltaService;
    private final AgentToolExecutorService toolExecutor;
    private final ToolRegistry toolRegistry;
    private final MeterRegistry meterRegistry;
    private final dev.mark.llm.service.LlmSettingsService llmSettingsService;
    private final dev.mark.skill.service.L0SkillEngine l0SkillEngine;
    private final dev.mark.trajectory.service.TrajectoryRecorderService trajectoryRecorderService;

    public AgentOrchestratorService(dev.mark.preference.UserPreferenceService userPreferenceService,
            LlmRouterService llmRouter,
            TaskRepository taskRepository, dev.mark.task.service.TaskSseService taskSseService,
            AgentEnvironmentObserver environmentObserver,
            AgentPropertiesConfig properties,
            AgentCompletionVerifierService completionVerifier,
            AgentPromptBuilderService promptBuilder,
            AgentEnvironmentDeltaService deltaService,
            AgentToolExecutorService toolExecutor,
            ToolRegistry toolRegistry, MeterRegistry meterRegistry,
            dev.mark.llm.service.LlmSettingsService llmSettingsService,
            dev.mark.skill.service.L0SkillEngine l0SkillEngine,
            dev.mark.trajectory.service.TrajectoryRecorderService trajectoryRecorderService) {
        this.userPreferenceService = userPreferenceService;
        this.llmRouter = llmRouter;
        this.taskRepository = taskRepository;
        this.taskSseService = taskSseService;
        this.environmentObserver = environmentObserver;
        this.properties = properties;
        this.completionVerifier = completionVerifier;
        this.promptBuilder = promptBuilder;
        this.llmSettingsService = llmSettingsService;
        this.deltaService = deltaService;
        this.toolExecutor = toolExecutor;
        this.toolRegistry = toolRegistry;
        this.meterRegistry = meterRegistry;
        this.l0SkillEngine = l0SkillEngine;
        this.trajectoryRecorderService = trajectoryRecorderService;
    }

    public Map<UUID, CompletableFuture<String>> getPendingReplies() {
        return pendingReplies;
    }

    public boolean submitReply(UUID taskId, String reply) {
        CompletableFuture<String> future = pendingReplies.remove(taskId);
        if (future != null) {
            future.complete(reply);
            return true;
        }
        return false;
    }

    public TaskResponseDTO executeTask(String taskId, String goal) {
        log.info("event=task_started taskId={}", taskId);
        Timer.Sample sample = Timer.start(meterRegistry);

        Long configId = taskRepository.findById(UUID.fromString(taskId))
                .map(TaskEntity::getConfigId)
                .orElse(null);

        AgentStateModel state = new AgentStateModel(taskId, goal, configId);
        state.setStatus("EXECUTING");
        setAction(state, "Executing Ultra Instinct Tier-0 Skill: " + goal);

        // Tier-0 Ultra Instinct Fast-Path Execution (< 50ms, 0 LLM calls)
        java.util.Optional<dev.mark.skill.dto.SkillExecutionResult> l0Result = l0SkillEngine.tryExecute(taskId, goal);
        if (l0Result.isPresent()) {
            dev.mark.skill.dto.SkillExecutionResult res = l0Result.get();
            for (dev.mark.agent.model.AgentStepModel step : res.steps()) {
                state.addStep(step);
            }
            if (res.success()) {
                state.setStatus("COMPLETED");
                state.setFinalAnswer(res.summary());
                state.setPlan(java.util.List.of("1. Execute Tier-0 Ultra Instinct skill", "2. Verify DOM delivery"));
                saveState(state);
                log.info("=== [ORCHESTRATOR:L0_SUCCESS] taskId={} Instant completion without LLM overhead", taskId);
                long duration = java.time.Duration.between(state.createdAt(), java.time.Instant.now()).toMillis();
                trajectoryRecorderService.recordTrajectory(
                        taskId,
                        goal,
                        null,
                        state.steps().stream().map(dev.mark.agent.model.AgentStepModel::toolName).toList(),
                        java.util.Map.of(),
                        true,
                        duration,
                        0
                );
                sample.stop(meterRegistry.timer("agent.task.duration"));
                return response(state);
            } else if (res.intervention() != null) {
                dev.mark.tool.dto.HumanInterventionRequest intervention = res.intervention();
                String displayTitle = "[" + intervention.type() + "] " + intervention.title();
                String prompt = intervention.prompt();
                if (intervention.mediaUri() != null) {
                    prompt = prompt + " (Media: " + intervention.mediaUri() + ")";
                }
                String currentActionText = displayTitle + ": " + prompt;
                state.setStatus("NEEDS_INPUT");
                setAction(state, currentActionText);

                UUID taskUuid = UUID.fromString(state.taskId());
                taskRepository.findById(taskUuid).ifPresent(t -> {
                    t.setStatus(dev.mark.agent.model.AgentStatusModel.NEEDS_INPUT);
                    t.setCurrentAction(currentActionText);
                    taskRepository.save(t);
                    taskSseService.broadcastTaskUpdate(t);
                });

                CompletableFuture<String> replyFuture = new CompletableFuture<>();
                pendingReplies.put(taskUuid, replyFuture);

                try {
                    String userReply = replyFuture.get(10, java.util.concurrent.TimeUnit.MINUTES);
                    log.info("=== [ORCHESTRATOR:USER_REPLIED_TO_L0] taskId={} reply='{}'", taskId, userReply);
                    java.util.Optional<dev.mark.skill.dto.SkillExecutionResult> retryResult = l0SkillEngine.tryExecute(taskId, goal);
                    if (retryResult.isPresent() && retryResult.get().success()) {
                        for (dev.mark.agent.model.AgentStepModel step : retryResult.get().steps()) {
                            state.addStep(step);
                        }
                        state.setStatus("COMPLETED");
                        state.setFinalAnswer(retryResult.get().summary());
                        saveState(state);
                        long duration = java.time.Duration.between(state.createdAt(), java.time.Instant.now()).toMillis();
                        trajectoryRecorderService.recordTrajectory(
                                taskId,
                                goal,
                                null,
                                state.steps().stream().map(dev.mark.agent.model.AgentStepModel::toolName).toList(),
                                java.util.Map.of(),
                                true,
                                duration,
                                0
                        );
                        sample.stop(meterRegistry.timer("agent.task.duration"));
                        return response(state);
                    }
                } catch (Exception e) {
                    pendingReplies.remove(taskUuid);
                    log.error("=== [ORCHESTRATOR:L0_INTERVENTION_ERROR] taskId={}", taskId, e);
                }
            }
        }

        state.setStatus("PLANNING");

        try {
            log.info(">>> [ORCHESTRATOR:PLAN_START] taskId={} config={} goal='{}'", taskId, configId, goal);
            List<String> plan = generatePlan(state, goal, configId);
            log.info("<<< [ORCHESTRATOR:PLAN_READY] taskId={} stepsCount={}", taskId, plan.size());
            state.setPlan(plan);
            state.setStatus("EXECUTING");
            saveState(state);

            int consecutiveRateLimits = 0;

            while ("EXECUTING".equals(state.status()) || "COOLDOWN".equals(state.status())
                    || "QUOTA_EXHAUSTED".equals(state.status())) {
                if ("COOLDOWN".equals(state.status()) || "QUOTA_EXHAUSTED".equals(state.status())) {
                    boolean isDailyQuota = "QUOTA_EXHAUSTED".equals(state.status());
                    consecutiveRateLimits++;

                    int maxKeys = Math.max(1, llmSettingsService.getApiKeysCount(state.configId()));

                    if (consecutiveRateLimits >= maxKeys) {
                        if (isDailyQuota) {
                            log.error("!!! [ORCHESTRATOR:FAILED] taskId={} Daily Quota Exhausted across all keys.",
                                    taskId);
                            handleFailure(state,
                                    "Task Failed: Daily LLM Quota Exhausted across all available API keys. Please try again tomorrow or add different API keys.");
                            break;
                        } else {
                            try {
                                log.warn("!!! [ORCHESTRATOR:COOLDOWN] taskId={} All keys rate limited. Sleeping 60s...",
                                        taskId);
                                setAction(state, "Rate Limited. Cooling down for 60 seconds...");
                                Thread.sleep(60000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                            }
                            consecutiveRateLimits = 0;
                            state.setStatus("EXECUTING");
                            saveState(state);
                        }
                    } else {
                        log.warn(
                                "!!! [ORCHESTRATOR:COOLDOWN] taskId={} Immediately retrying with next API key (attempt {}/{})",
                                taskId, consecutiveRateLimits, maxKeys);
                        state.setStatus("EXECUTING");
                        saveState(state);
                    }
                } else {
                    executeNextStep(state);
                    if (!"COOLDOWN".equals(state.status()) && !"QUOTA_EXHAUSTED".equals(state.status())) {
                        consecutiveRateLimits = 0;
                    }
                }
            }

            if ("COMPLETED".equals(state.status())) {
                meterRegistry.counter("agent.task.status", "status", "success").increment();
            } else {
                meterRegistry.counter("agent.task.status", "status", "failed").increment();
            }

        } catch (Throwable e) {
            log.error("!!! [ORCHESTRATOR:FAILED] taskId={} error={}", taskId, e.getMessage(), e);
            meterRegistry.counter("agent.task.status", "status", "error").increment();
            return handleFailure(state, failureMessage(e));
        } finally {
            sample.stop(meterRegistry.timer("agent.task.duration"));
        }

        log.info("=== [ORCHESTRATOR:FINISHED] taskId={} status={}", taskId, state.status());
        if ("COMPLETED".equals(state.status())) {
            long duration = java.time.Duration.between(state.createdAt(), java.time.Instant.now()).toMillis();
            trajectoryRecorderService.recordTrajectory(
                    state.taskId(),
                    state.goal(),
                    null,
                    state.steps().stream().map(dev.mark.agent.model.AgentStepModel::toolName).toList(),
                    java.util.Map.of(),
                    true,
                    duration,
                    state.llmRequestCount()
            );
        }
        return response(state);
    }

    private List<String> generatePlan(AgentStateModel state, String goal, Long configId) {
        LlmRequestDTO request = new LlmRequestDTO(
                state.taskId(),
                "Planning",
                configId,
                promptBuilder.systemPromptForPlanning(),
                null,
                List.of(),
                null,
                List.of(LlmMessageDTO.userMessage(goal)));

        PlanResponseDTO planResponse = null;
        int maxKeys = Math.max(1, llmSettingsService.getApiKeysCount(configId));
        for (int i = 0; i < maxKeys; i++) {
            try {
                state.incrementLlmRequestCount();
                planResponse = llmRouter.planStream(request, chunk -> taskSseService.broadcastThoughtChunk(chunk));
                break;
            } catch (dev.mark.llm.exception.LlmProviderException e) {
                if (e.getMessage() != null && e.getMessage().contains("429")) {
                    log.warn("--- [ORCHESTRATOR:PLAN_429] Rate limited on key index {}, rotating...", i);

                } else {
                    throw e;
                }
            }
        }
        if (planResponse == null)
            throw new dev.mark.llm.exception.LlmProviderException(
                    "LLM planning request failed: 429 Quota/Rate Limit Exceeded across all keys");
        if (planResponse.steps() == null || planResponse.steps().isEmpty()) {
            log.warn("--- [ORCHESTRATOR:PLAN_FALLBACK] taskId=unknown goal='{}' using single step fallback", goal);
            return List.of(goal); // fallback to goal as single step
        }
        return planResponse.steps();
    }

    private void executeNextStep(AgentStateModel state) {
        if (state.steps().size() >= userPreferenceService.getInt("agent.maxSteps", properties.maxSteps())) {
            log.warn("!!! [ORCHESTRATOR:MAX_STEPS] taskId={} reached max steps limit={}", state.taskId(),
                    userPreferenceService.getInt("agent.maxSteps", properties.maxSteps()));
            handleFailure(state, "Max steps reached ("
                    + userPreferenceService.getInt("agent.maxSteps", properties.maxSteps()) + ")");
            return;
        }

        int stepNumber = state.steps().size() + 1;
        int activePlanStep = state.currentPlanIndex() + 1;
        log.info(">>> [ORCHESTRATOR:STEP_START] taskId={} step={}/{} activePlanStep={}", state.taskId(), stepNumber,
                userPreferenceService.getInt("agent.maxSteps", properties.maxSteps()), activePlanStep);

        AgentWorldStateModel currentWorldState = environmentObserver.observe();
        AgentWorldStateDeltaModel stateDelta = currentWorldState.compareTo(state.latestWorldState());
        state.updateWorldState(currentWorldState);

        String systemPrompt = promptBuilder.systemPromptForExecution(state.plan(), currentWorldState,
                deltaService.formatWorldStateDelta(stateDelta), activePlanStep);

        List<LlmMessageDTO> context = buildContext(state);
        String activeStepText = (state.plan() != null && activePlanStep <= state.plan().size())
                ? state.plan().get(activePlanStep - 1)
                : "Continue execution.";
        boolean isCompletionStep = activeStepText.toLowerCase().contains("complete")
                || activePlanStep > (state.plan() != null ? state.plan().size() : 0);
        String stepInstruction = isCompletionStep
                ? "\nINSTRUCTION: Active Plan Step " + activePlanStep + " is TASK COMPLETION. All action steps are already finished and verified. You MUST call 'task_complete' with a concise summary of the results, or return your final answer directly WITHOUT calling any action tools. Do NOT repeat previous action tools."
                : "\nINSTRUCTION: Execute ONLY the action required by Active Plan Step " + activePlanStep + ". If it specifies 'open_session', you MUST call 'open_session'. Do NOT skip to subsequent steps.";
        String userPrompt = "Overall Mission Goal: " + state.goal()
                + "\nActive Plan Step " + activePlanStep + ": " + activeStepText
                + stepInstruction
                + "\nCRITICAL: Always use the exact folder paths, file names, contacts, and application names specified in the Overall Mission Goal. Never use placeholder or dummy paths.";
        LlmRequestDTO request = new LlmRequestDTO(
                state.taskId(),
                "Execution",
                state.configId(),
                systemPrompt,
                userPrompt,
                toolRegistryDefinitions(),
                "auto",
                context);

        try {
            setAction(state, "Thinking (Waiting for LLM)...");
            state.incrementLlmRequestCount();
            LlmResponseDTO response = llmRouter.completeStream(request,
                    chunk -> taskSseService.broadcastThoughtChunk(chunk));
            setAction(state, "Analyzing LLM response...");

            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                log.info("--- [ORCHESTRATOR:BATCH_RECEIVED] taskId={} step={} toolCallsCount={}", state.taskId(),
                        stepNumber, response.toolCalls().size());
                LlmMessageDTO assistantMessage = LlmMessageDTO.assistantToolCalls(response.toolCalls());
                state.addMessage(assistantMessage);

                List<LlmToolObservationDTO> observations = new ArrayList<>();
                boolean shouldAbortBatch = false;
                boolean transitionDetected = false;

                for (LlmToolCallDTO toolCall : response.toolCalls()) {
                    if (shouldAbortBatch) {
                        log.info(
                                "--- [ORCHESTRATOR:TOOL_SKIPPED] taskId={} step={} tool={} reason='Aborted due to previous failure or UI transition'",
                                state.taskId(), stepNumber, toolCall.name());
                        observations.add(new LlmToolObservationDTO(toolCall.id(), toolCall.name(), false,
                                "Aborted due to previous tool failure or UI transition.", Map.of()));
                        continue;
                    }

                    // GUARDRAIL: Loop detection
                    int loopCount = 0;
                    for (int i = state.messages().size() - 1; i >= 0; i--) {
                        LlmMessageDTO msg = state.messages().get(i);
                        if ("assistant".equals(msg.role()) && msg.toolCalls() != null && !msg.toolCalls().isEmpty()) {
                            boolean foundMatch = false;
                            for (LlmToolCallDTO prevCall : msg.toolCalls()) {
                                if (prevCall.name().equals(toolCall.name())
                                        && prevCall.arguments().equals(toolCall.arguments())) {
                                    loopCount++;
                                    foundMatch = true;
                                    break;
                                }
                            }
                            if (!foundMatch)
                                break;
                        } else if ("user".equals(msg.role()) && msg.content() != null
                                && (msg.content().startsWith("SYSTEM AUTO-OBSERVATION") || msg.content().startsWith("SYSTEM GUIDANCE"))) {
                            // ignore auto-observations and guidance in between loop checks
                            continue;
                        } else if (!"tool".equals(msg.role())) {
                            break;
                        }
                    }

                    if (loopCount >= 4) { // 4 because it will find itself in the current assistantMessage once
                        log.warn("!!! [ORCHESTRATOR:GUARDRAIL] taskId={} Loop detected. Repeated tool call 3 times: {}",
                                state.taskId(), toolCall.name());
                        meterRegistry.counter("agent.task.status", "status", "guardrail_blocked").increment();
                        handleFailure(state, "Guardrail triggered: Infinite loop detected for tool " + toolCall.name());
                        return;
                    }

                    log.info("--- [ORCHESTRATOR:CALLING_TOOL] taskId={} step={} tool={}", state.taskId(), stepNumber,
                            toolCall.name());
                    setAction(state, "Executing tool: " + toolCall.name() + "...");
                    ToolResultDTO toolResult = toolExecutor.execute(state.taskId(), toolCall);
                    setAction(state, "Tool execution completed.");

                    String outcome = toolResult.successful() ? "Success" : "Failed";
                    String obsText = toolResult.observation() != null ? toolResult.observation() : "";
                    String details = obsText
                            + (toolResult.metadata() != null && !toolResult.metadata().isEmpty()
                                    ? "\nData: " + toolResult.metadata()
                                    : "");

                    LlmToolObservationDTO observation = new LlmToolObservationDTO(toolCall.id(), toolCall.name(),
                            toolResult.successful(), details, toolResult.metadata());
                    observations.add(observation);

                    String argsJson = "{}";
                    try {
                        argsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                                .writeValueAsString(toolCall.arguments());
                    } catch (Exception ignored) {
                    }
                    state.addStep(new AgentStepModel(stepNumber, response.content(), toolCall.name(), argsJson,
                            outcome + ": " + details, response.provider(), java.time.Instant.now()));

                    if (toolResult.successful() && !"task_complete".equals(toolCall.name()) && !"ask_user".equals(toolCall.name())) {
                        state.advancePlanIndex();
                    }

                    // Auto-complete if a verification tool confirmed success and no action steps remain
                    if (toolResult.successful() && Boolean.TRUE.equals(toolResult.metadata().get("verified"))) {
                        boolean onlyCompletionRemains = state.plan() == null
                                || state.currentPlanIndex() >= state.plan().size()
                                || (state.currentPlanIndex() == state.plan().size() - 1
                                        && state.plan().get(state.currentPlanIndex()).toLowerCase().contains("complete"));
                        if (onlyCompletionRemains) {
                            log.info("=== [ORCHESTRATOR:AUTO_COMPLETE_VERIFIED] taskId={} Tool '{}' verified goal. Auto-completing task.",
                                    state.taskId(), toolCall.name());
                            state.setStatus("COMPLETED");
                            String completionSummary = "Task successfully completed and verified: " + obsText;
                            state.setFinalAnswer(completionSummary);
                            for (LlmToolObservationDTO obs : observations) {
                                state.addMessage(LlmMessageDTO.toolObservation(obs));
                            }
                            state.addStep(new AgentStepModel(stepNumber + 1, "Task Verified Complete", "task_complete", "{}",
                                    completionSummary, response.provider(), java.time.Instant.now()));
                            saveState(state);
                            return;
                        }
                    }

                    if ("task_complete".equals(toolCall.name()) && toolResult.successful()) {
                        String summaryStr = toolCall.arguments().get("summary") instanceof String s ? s.trim().toLowerCase() : "";
                        if (summaryStr.contains("no task completed") || summaryStr.contains("not completed yet") || summaryStr.contains("nothing completed")) {
                            log.warn("=== [ORCHESTRATOR:REJECT_TASK_COMPLETE] taskId={} Rejected false completion: '{}'", state.taskId(), summaryStr);
                            shouldAbortBatch = true;
                        } else {
                            log.info("=== [ORCHESTRATOR:TASK_COMPLETE] taskId={} marked complete via task_complete tool", state.taskId());
                            state.setStatus("COMPLETED");
                            state.setFinalAnswer(details);
                            for (LlmToolObservationDTO obs : observations) {
                                state.addMessage(LlmMessageDTO.toolObservation(obs));
                            }
                            saveState(state);
                            return;
                        }
                    }

                    if (toolResult.interventionRequest() != null || "ask_user".equals(toolCall.name())) {
                        dev.mark.tool.dto.HumanInterventionRequest intervention = toolResult.interventionRequest();
                        String prompt;
                        String displayTitle;
                        if (intervention != null) {
                            prompt = intervention.prompt();
                            displayTitle = "[" + intervention.type() + "] " + intervention.title();
                            if (intervention.mediaUri() != null) {
                                prompt = prompt + " (Media: " + intervention.mediaUri() + ")";
                            }
                        } else {
                            prompt = dev.mark.tool.impl.system.AskUserTool.extractQuestion(toolCall.arguments());
                            displayTitle = "[CLARIFICATION] Guidance Needed";
                        }

                        log.info("=== [ORCHESTRATOR:WAITING_USER_INPUT] taskId={} title='{}' prompt='{}'",
                                state.taskId(), displayTitle, prompt);
                        state.setStatus("NEEDS_INPUT");
                        String currentActionText = displayTitle + ": " + prompt;
                        setAction(state, currentActionText);

                        UUID taskUuid = UUID.fromString(state.taskId());
                        taskRepository.findById(taskUuid).ifPresent(t -> {
                            t.setStatus(dev.mark.agent.model.AgentStatusModel.NEEDS_INPUT);
                            t.setCurrentAction(currentActionText);
                            taskRepository.save(t);
                            taskSseService.broadcastTaskUpdate(t);
                        });

                        CompletableFuture<String> replyFuture = new CompletableFuture<>();
                        pendingReplies.put(taskUuid, replyFuture);

                        try {
                            String userReply = replyFuture.get(10, java.util.concurrent.TimeUnit.MINUTES);
                            log.info("=== [ORCHESTRATOR:USER_REPLIED] taskId={} reply='{}'", state.taskId(), userReply);
                            details = "User provided response: " + userReply;
                            observation = new LlmToolObservationDTO(toolCall.id(), toolCall.name(), true, details,
                                    Map.of("reply", userReply, "intervention_completed", true));
                            observations.set(observations.size() - 1, observation);

                            state.setStatus("EXECUTING");
                            taskRepository.findById(taskUuid).ifPresent(t -> {
                                t.setStatus(dev.mark.agent.model.AgentStatusModel.EXECUTING);
                                t.setCurrentAction("User reply received. Resuming task execution...");
                                taskRepository.save(t);
                                taskSseService.broadcastTaskUpdate(t);
                            });
                        } catch (java.util.concurrent.TimeoutException te) {
                            pendingReplies.remove(taskUuid);
                            log.warn("=== [ORCHESTRATOR:USER_TIMEOUT] taskId={} Timed out waiting for reply", state.taskId());
                            details = "Timed out waiting for user response after 10 minutes.";
                            observation = new LlmToolObservationDTO(toolCall.id(), toolCall.name(), false, details, Map.of());
                            observations.set(observations.size() - 1, observation);
                        } catch (Throwable e) {
                            pendingReplies.remove(taskUuid);
                            log.error("=== [ORCHESTRATOR:USER_INPUT_ERROR] taskId={}", state.taskId(), e);
                            details = "Error while awaiting user input: " + e.getMessage();
                            observation = new LlmToolObservationDTO(toolCall.id(), toolCall.name(), false, details, Map.of());
                            observations.set(observations.size() - 1, observation);
                        }
                    }

                    if (!toolResult.successful()
                            || Boolean.TRUE.equals(toolResult.metadata().get("transition_detected"))) {
                        log.info(
                                "--- [ORCHESTRATOR:ABORT_BATCH] taskId={} Tool failed or transition detected, aborting subsequent tool calls in batch.",
                                state.taskId());
                        shouldAbortBatch = true;
                        if (Boolean.TRUE.equals(toolResult.metadata().get("transition_detected"))) {
                            transitionDetected = true;
                        }
                    }
                }

                for (LlmToolObservationDTO obs : observations) {
                    state.addMessage(LlmMessageDTO.toolObservation(obs));
                }

                // Adaptive failure recovery: If a tool failed repeatedly, steer the model before the loop guardrail hits
                for (LlmToolObservationDTO obs : observations) {
                    if (!obs.successful()) {
                        long failureCount = state.steps().stream()
                                .filter(s -> obs.toolName().equals(s.toolName()) && s.outcome() != null && s.outcome().startsWith("Failed"))
                                .count();
                        if (failureCount >= 2) {
                            String guidance = "SYSTEM GUIDANCE: The tool '" + obs.toolName()
                                    + "' has failed repeatedly. Do NOT call the exact same tool with identical arguments again. "
                                    + "Either try an alternative strategy/tool, verify required preconditions, or call 'ask_user' to explain the blocker and request guidance.";
                            log.info("--- [ORCHESTRATOR:GUIDANCE_INJECTED] taskId={} guidance='{}'", state.taskId(), guidance);
                            state.addMessage(LlmMessageDTO.userMessage(guidance));
                            break;
                        }
                    }
                }

                if (transitionDetected) {
                    log.info(
                            "--- [ORCHESTRATOR:AUTO_OBSERVE] taskId={} Transition detected, performing automatic inspect_active",
                            state.taskId());
                    LlmToolCallDTO autoInspectCall = new LlmToolCallDTO("auto-inspect", "inspect_ui",
                            Map.of("action", "inspect_active"));
                    ToolResultDTO inspectRes = toolExecutor.execute(state.taskId(), autoInspectCall);
                    LlmMessageDTO autoObserveMsg = LlmMessageDTO.userMessage(
                            "SYSTEM AUTO-OBSERVATION: A UI transition interrupted your action segment. " +
                                    "Here is the updated semantic UI state:\n" + inspectRes.observation());
                    state.addMessage(autoObserveMsg);
                }

                saveState(state);

            } else {
                // If model returned empty content with no tools while plan steps remain, re-prompt specifically for the active step
                if ((response.content() == null || response.content().isBlank())
                        && state.plan() != null && activePlanStep <= state.plan().size()) {
                    log.warn("--- [ORCHESTRATOR:EMPTY_LLM_RESPONSE] taskId={} step={} LLM returned empty response while step {} ('{}') is pending. Prompting retry...",
                            state.taskId(), stepNumber, activePlanStep, activeStepText);
                    LlmMessageDTO promptRetry = LlmMessageDTO.userMessage(
                            "SYSTEM NOTICE: Your previous response was empty and called no tools. "
                            + "Active Plan Step " + activePlanStep + ": '" + activeStepText + "' is NOT completed yet. "
                            + "You MUST call the appropriate tool to execute Active Plan Step " + activePlanStep + " now.");
                    state.addMessage(promptRetry);
                    state.addStep(new AgentStepModel(stepNumber, "", "None", "",
                            "Empty LLM response received while active step is pending; prompt retry injected.", response.provider(), java.time.Instant.now()));
                    saveState(state);
                    return;
                }

                // No tools called, agent believes it is done.
                log.info("--- [ORCHESTRATOR:NO_TOOLS] taskId={} step={} agent returned direct completion",
                        state.taskId(), stepNumber);
                state.addMessage(LlmMessageDTO.assistantCompletion(response.content()));

                AgentVerificationResultModel verification = completionVerifier.verify(state.goal(), state.messages(),
                        currentWorldState);
                if (verification.verified()) {
                    log.info("=== [ORCHESTRATOR:VERIFIED] taskId={} step={} Task Complete! reason='{}'",
                            state.taskId(), stepNumber, verification.reason());
                    state.setStatus("COMPLETED");
                    state.setFinalAnswer(response.content());
                    state.addStep(new AgentStepModel(stepNumber, "Task Verified Complete", "None", "",
                            verification.reason(), response.provider(), java.time.Instant.now()));
                    saveState(state);
                } else {
                    log.warn(
                            "=== [ORCHESTRATOR:CRITIQUE] taskId={} step={} Verification Failed! reason='{}' -> looping back",
                            state.taskId(), stepNumber, verification.reason());
                    LlmMessageDTO critiqueMessage = LlmMessageDTO
                            .userMessage("System Critique: Task is not complete. " + verification.reason());
                    state.addMessage(critiqueMessage);
                    state.addStep(new AgentStepModel(stepNumber, response.content(), "Verification Failed", "",
                            verification.reason(), response.provider(), java.time.Instant.now()));
                    saveState(state);
                }
            }

        } catch (LlmProviderException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("429") || msg.contains("quota")) {
                if (msg.contains("perday") || msg.contains("daily")) {
                    log.warn("!!! [ORCHESTRATOR:QUOTA_EXHAUSTED] taskId={} step={} Daily quota exhausted.",
                            state.taskId(), stepNumber);
                    state.setStatus("QUOTA_EXHAUSTED");
                    saveState(state);
                } else {
                    log.warn("!!! [ORCHESTRATOR:RATE_LIMIT] taskId={} step={} Rate limit hit. Entering COOLDOWN.",
                            state.taskId(), stepNumber);
                    state.setStatus("COOLDOWN");
                    saveState(state);
                }
            } else {
                log.error("!!! [ORCHESTRATOR:LLM_ERROR] taskId={} step={} error={}", state.taskId(), stepNumber,
                        e.getMessage());
                handleFailure(state, "LLM Provider Error: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("!!! [ORCHESTRATOR:STEP_ERROR] taskId={} step={} unexpected error: {}", state.taskId(), stepNumber,
                    e.getMessage(), e);
            handleFailure(state, "Step execution error: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
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
        state.addStep(
                new AgentStepModel(step, "Agent execution failed", "", "", reason, "system", java.time.Instant.now()));
        log.warn("event=task_failed taskId={} reason={}", state.taskId(), reason);
        return response(state);
    }

    private TaskResponseDTO response(AgentStateModel state) {
        saveState(state);
        return new TaskResponseDTO(java.util.UUID.fromString(state.taskId()), state.goal(),
                dev.mark.agent.model.AgentStatusModel.valueOf(state.status()), state.finalAnswer(),
                state.currentAction(), state.plan(), state.llmRequestCount(), state.steps());
    }

    private void setAction(AgentStateModel state, String action) {
        state.setCurrentAction(action);
        saveState(state);
    }

    private void saveState(AgentStateModel state) {
        TaskEntity entity = new TaskEntity(java.util.UUID.fromString(state.taskId()), state.goal(),
                dev.mark.agent.model.AgentStatusModel.valueOf(state.status()), state.finalAnswer(), state.createdAt());
        entity.setConfigId(state.configId());
        entity.setPlan(state.plan());
        entity.setCurrentAction(state.currentAction());
        entity.setLlmRequestCount(state.llmRequestCount());
        List<AgentStepEmbeddableEntity> embeddables = state.steps().stream()
                .map(s -> new AgentStepEmbeddableEntity(s.number(), s.description(), s.toolName(), s.toolArguments(),
                        s.outcome(), s.provider(), s.timestamp()))
                .collect(Collectors.toList());
        entity.setSteps(embeddables);
        taskRepository.save(entity);
        taskSseService.broadcastTaskUpdate(entity);
    }

    private String failureMessage(Throwable exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
