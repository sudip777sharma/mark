package dev.mark.agent;

import dev.mark.llm.LlmMessage;
import dev.mark.llm.LlmRequest;
import dev.mark.llm.LlmResponse;
import dev.mark.llm.LlmProvider;
import dev.mark.llm.PlanResponse;
import dev.mark.llm.LlmToolCall;
import dev.mark.llm.LlmToolDefinition;
import dev.mark.llm.LlmToolObservation;
import dev.mark.task.TaskRequest;
import dev.mark.task.TaskResponse;
import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import dev.mark.tool.ToolRegistry;
import dev.mark.task.data.AgentStepEmbeddable;
import dev.mark.task.data.TaskEntity;
import dev.mark.task.data.TaskRepository;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AgentEngine {
    private static final Logger log = LoggerFactory.getLogger(AgentEngine.class);
    public static final Map<UUID, CompletableFuture<String>> PENDING_REPLIES = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final LlmProvider llmProvider;
    private final CompletionVerifier completionVerifier;
    private final AgentProperties properties;
    private final TaskRepository taskRepository;

    private final EnvironmentObserver environmentObserver;

    public AgentEngine(ToolRegistry toolRegistry, LlmProvider llmProvider, CompletionVerifier completionVerifier, AgentProperties properties, TaskRepository taskRepository, EnvironmentObserver environmentObserver) {
        this.toolRegistry = toolRegistry;
        this.llmProvider = llmProvider;
        this.completionVerifier = completionVerifier;
        this.properties = properties;
        this.taskRepository = taskRepository;
        this.environmentObserver = environmentObserver;
    }

    public TaskResponse submit(TaskRequest request) {
        AgentState state = new AgentState(UUID.randomUUID(), request.goal());
        state.transitionTo(AgentStatus.PLANNING);
        saveState(state);
        log.info("event=task_created taskId={} goal={}", state.taskId(), state.goal());
        
        CompletableFuture.runAsync(() -> executeTask(state, request));
        
        return new TaskResponse(state.taskId(), state.goal(), state.status(), state.finalAnswer(), state.plan(), state.steps());
    }

    void executeTask(AgentState state, TaskRequest request) {
        List<LlmMessage> history = new ArrayList<>();
        List<LlmToolObservation> observations = new ArrayList<>();
        
        try {
            PlanResponse plan = llmProvider.plan(new LlmRequest(request.provider(), systemPromptForPlanning(), request.goal(), availableToolDefinitions(), "auto", List.of()));
            state.setPlan(plan.steps());
            state.addStep(new AgentStep(0, "Generated execution plan", "", plan.steps().toString(), "gemini"));
            state.transitionTo(AgentStatus.EXECUTING);
        } catch (RuntimeException exception) {
            fail(state, 0, "Planning failed: " + failureMessage(exception));
            return;
        }

        saveState(state);

        for (int step = 1; step <= properties.maxSteps(); step++) {
            WorldState worldState = environmentObserver.observe();
            List<LlmMessage> compactedHistory = compactHistory(history, properties);

            LlmResponse response;
            try {
                response = llmProvider.complete(new LlmRequest(request.provider(), systemPromptForExecution(state.plan(), worldState), request.goal(), availableToolDefinitions(), "auto", compactedHistory));
            } catch (RuntimeException exception) {
                fail(state, step, "LLM request failed: " + failureMessage(exception));
                return;
            }
            if (response == null) {
                fail(state, step, "Malformed LLM response: provider returned no response");
                return;
            }

            if (response.toolCalls().isEmpty()) {
                if ((response.content() == null || response.content().isBlank()) && observations.isEmpty()) {
                    fail(state, step, "Malformed LLM response: no tool call or completion content");
                    return;
                }
                String completionContent = (response.content() != null && !response.content().isBlank())
                        ? response.content() : "(implicit completion after tool execution)";
                history.add(LlmMessage.assistantCompletion(completionContent));
                state.setFinalAnswer(completionContent);
                state.transitionTo(AgentStatus.VERIFYING);
                VerificationResult verification = completionVerifier.verify(observations);
                if (!verification.verified()) {
                    fail(state, step, verification.reason());
                    return;
                }
                state.addStep(new AgentStep(step, "Verify LLM completion", "", verification.reason(), "system"));
                state.transitionTo(AgentStatus.COMPLETED);
                log.info("event=task_finished taskId={} status={}", state.taskId(), state.status());
                saveState(state);
                return;
            }

            if (response.toolCalls().size() != 1) {
                fail(state, step, "Malformed LLM response: exactly one tool call is required per step");
                return;
            }

            LlmToolCall toolCall = response.toolCalls().getFirst();
            history.add(LlmMessage.assistantToolCall(toolCall));
            state.addStep(new AgentStep(step, "Tool call: " + toolCall.name(), toolCall.name(), toolCall.arguments().toString(), response.provider()));
            saveState(state);
            
            if (toolCall.name().equals("execute_command")) {
                String cmd = String.valueOf(toolCall.arguments().get("command"));
                if (isDangerousCommand(cmd)) {
                    state.transitionTo(AgentStatus.NEEDS_INPUT);
                    saveState(state);
                    log.info("event=task_paused_for_approval taskId={} cmd={}", state.taskId(), cmd);
                    
                    CompletableFuture<String> replyFuture = new CompletableFuture<>();
                    PENDING_REPLIES.put(state.taskId(), replyFuture);
                    String userReply = replyFuture.join();
                    PENDING_REPLIES.remove(state.taskId());
                    
                    if (!userReply.trim().equalsIgnoreCase("approve")) {
                        LlmToolObservation obs = new LlmToolObservation(toolCall.id(), toolCall.name(), false, "User rejected the command: " + userReply, Map.of());
                        observations.add(obs);
                        history.add(LlmMessage.toolObservation(obs));
                        state.transitionTo(AgentStatus.EXECUTING);
                        state.addStep(new AgentStep(step, "User rejected command", "", userReply, "user"));
                        saveState(state);
                        continue;
                    }
                    
                    state.transitionTo(AgentStatus.EXECUTING);
                    state.addStep(new AgentStep(step, "User approved command", "", "approve", "user"));
                    saveState(state);
                }
            }

            state.transitionTo(AgentStatus.EXECUTING);
            LlmToolObservation observation = executeTool(state, toolCall);
            observations.add(observation);
            history.add(LlmMessage.toolObservation(observation));
            
            if (observation.metadata() != null && observation.metadata().containsKey("base64Image")) {
                String b64 = (String) observation.metadata().get("base64Image");
                String mime = (String) observation.metadata().get("mimeType");
                history.add(LlmMessage.userMessageWithImage("Here is the requested image:", b64, mime));
            }
            
            state.transitionTo(AgentStatus.VERIFYING);
            state.addStep(new AgentStep(step, "Record tool observation", toolCall.name(), observation.observation(), "system"));
            log.info("event=tool_observed taskId={} tool={} successful={}", state.taskId(), toolCall.name(), observation.successful());
            state.transitionTo(AgentStatus.EXECUTING);
            saveState(state);

            if (toolCall.name().equals("ask_user")) {
                state.transitionTo(AgentStatus.NEEDS_INPUT);
                saveState(state);
                log.info("event=task_paused taskId={} tool=ask_user", state.taskId());
                
                CompletableFuture<String> replyFuture = new CompletableFuture<>();
                PENDING_REPLIES.put(state.taskId(), replyFuture);
                String userReply = replyFuture.join();
                PENDING_REPLIES.remove(state.taskId());
                
                state.transitionTo(AgentStatus.EXECUTING);
                history.add(LlmMessage.userMessage("User replied: " + userReply));
                state.addStep(new AgentStep(step, "Received user reply", "", userReply, "user"));
                saveState(state);
                log.info("event=task_resumed taskId={}", state.taskId());
            }
        }

        fail(state, properties.maxSteps(), "Maximum agent steps exceeded");
    }

    private LlmToolObservation executeTool(AgentState state, LlmToolCall toolCall) {
        try {
            Tool tool = toolRegistry.require(toolCall.name());
            ToolResult result = tool.execute(new ToolRequest(state.taskId(), state.goal(), toolCall.arguments()));
            return new LlmToolObservation(toolCall.id(), toolCall.name(), result.successful(), result.observation(), result.metadata());
        } catch (RuntimeException exception) {
            return new LlmToolObservation(toolCall.id(), toolCall.name(), false, "Tool execution failed: " + failureMessage(exception), Map.of());
        }
    }

    private List<LlmToolDefinition> availableToolDefinitions() {
        return toolRegistry.availableTools().stream()
                .map(tool -> new LlmToolDefinition(tool.name(), tool.description(), tool.parameterSchema()))
                .toList();
    }

    private TaskResponse fail(AgentState state, int step, String reason) {
        if (state.status() != AgentStatus.FAILED) state.transitionTo(AgentStatus.FAILED);
        state.addStep(new AgentStep(step, "Agent execution failed", "", reason, "system"));
        log.warn("event=task_failed taskId={} reason={}", state.taskId(), reason);
        return response(state);
    }

    private TaskResponse response(AgentState state) {
        saveState(state);
        return new TaskResponse(state.taskId(), state.goal(), state.status(), state.finalAnswer(), state.plan(), state.steps());
    }

    private void saveState(AgentState state) {
        TaskEntity entity = new TaskEntity(state.taskId(), state.goal(), state.status(), state.finalAnswer(), state.createdAt());
        entity.setPlan(state.plan());
        List<AgentStepEmbeddable> embeddables = state.steps().stream()
                .map(s -> new AgentStepEmbeddable(s.number(), s.description(), s.toolName(), s.outcome(), s.provider()))
                .toList();
        entity.setSteps(embeddables);
        taskRepository.save(entity);
    }

    private String systemPromptForPlanning() {
        return "You are a master planner for an autonomous agent. Break down the user's goal into a sequential, logical list of steps using ONLY the available tools. Keep steps concise and actionable.";
    }

    private String systemPromptForExecution(List<String> plan, WorldState worldState) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current Environment:\n");
        sb.append("- Active application: ")
                .append(worldState.activeApplication())
                .append("\n");
        sb.append("- Active window: ")
                .append(worldState.activeWindow())
                .append("\n\n");

        sb.append("You are the reasoning component of MARK, an autonomous agent. ");
        sb.append("Select the BEST tool for the task. Available tools:\n");
        for (Tool tool : toolRegistry.availableTools()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        sb.append("\nRules:\n");
        sb.append("- Use ONLY the tools listed above. Never invent tools.\n");
        sb.append("- Match the task to the most specific tool. For file operations use read_file/write_file/list_directory, for math use calculate, for echoing use echo.\n");
        sb.append("- Return exactly one tool call when work remains.\n");
        sb.append("- When the task is complete, return a concise final response WITHOUT a tool call.\n");
        sb.append("- Do not claim success before tool results provide evidence.\n");
        sb.append("- If you perform a desktop_automation action that opens an application or triggers a slow UI update, you MUST use the 'delay' action to wait before taking the next step.\n");
        sb.append("- If you perform a browser action (navigate, click, type), you MUST verify the result by using browser_read_page or browser_extract before returning COMPLETED.\n");
        sb.append("- DESKTOP UI WORKFLOW: Before interacting with a native desktop application, ALWAYS use 'inspect_ui' first to discover UI elements and their bounding rectangles. Use these coordinates for desktop_automation actions.\n");
        sb.append("- When using desktop_automation for a specific application, ALWAYS set the 'target_window' parameter to the application's title substring. This ensures focus is verified and restored before each action.\n");
        sb.append("- If inspect_ui fails or returns insufficient data for a UI element, use 'screenshot' as a fallback to capture what is on screen.\n");

        if (plan != null && !plan.isEmpty()) {
            sb.append("\nYour Execution Plan:\n");
            for (int i = 0; i < plan.size(); i++) {
                sb.append((i + 1)).append(". ").append(plan.get(i)).append("\n");
            }
            sb.append("\nFollow this plan closely, step by step.");
        }

        return sb.toString();
    }

    private String failureMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
    private List<LlmMessage> compactHistory(List<LlmMessage> fullHistory, AgentProperties properties) {
        if (fullHistory.isEmpty()) return fullHistory;

        List<LlmMessage> compacted = new java.util.ArrayList<>(fullHistory);
        if (properties.maxHistoryLength() != null && compacted.size() > properties.maxHistoryLength()) {
            compacted = new java.util.ArrayList<>(compacted.subList(compacted.size() - properties.maxHistoryLength(), compacted.size()));
        }

        if (properties.maxHistoryChars() != null) {
            // We want to keep at least 2-4 messages to retain immediate context
            while (compacted.size() > 4) {
                int totalChars = compacted.stream().mapToInt(this::messageLength).sum();
                if (totalChars <= properties.maxHistoryChars()) {
                    break;
                }
                compacted.removeFirst();
            }
        }
        return compacted;
    }

    private int messageLength(LlmMessage msg) {
        int len = 0;
        if (msg.content() != null) len += msg.content().length();
        if (msg.toolObservation() != null && msg.toolObservation().observation() != null) {
            len += msg.toolObservation().observation().length();
        }
        for (dev.mark.llm.LlmToolCall tc : msg.toolCalls()) {
            if (tc.arguments() != null) {
                len += tc.arguments().toString().length();
            }
        }
        return len;
    }

    private boolean isDangerousCommand(String cmd) {
        if (cmd == null) return false;
        String lower = cmd.toLowerCase();
        return lower.contains("rm ") || lower.contains("del ") || lower.contains("format ") 
            || lower.contains("remove-item") || lower.contains("stop-process")
            || lower.contains("restart-computer") || lower.contains("shutdown");
    }
}
