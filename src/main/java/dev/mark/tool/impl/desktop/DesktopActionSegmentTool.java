package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.agent.model.action.*;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.service.ui.ElementResolver;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.mark.agent.service.TransitionAnalyzerService;

@Component
public class DesktopActionSegmentTool implements Tool {

    private final ElementResolver elementResolver;
    private final DesktopActionExecutorService executorService;
    private final ObjectMapper objectMapper;
    private final TransitionAnalyzerService transitionAnalyzerService;

    public DesktopActionSegmentTool(ElementResolver elementResolver, DesktopActionExecutorService executorService, ObjectMapper objectMapper, TransitionAnalyzerService transitionAnalyzerService) {
        this.elementResolver = elementResolver;
        this.executorService = executorService;
        this.objectMapper = objectMapper;
        this.transitionAnalyzerService = transitionAnalyzerService;
    }

    @Override
    public String name() {
        return "desktop_action_segment";
    }

    @Override
    public String description() {
        return "Executes a planned sequence of actions continuously until completion or failure.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "actions", Map.of("type", "array", "description", "List of Action objects", "items", Map.of("type", "object"))
            ),
            "required", java.util.List.of("actions")
        );
    }

    @Override
    public ToolResultDTO execute(dev.mark.tool.dto.ToolRequestDTO request) {
        String taskId = request.taskId() != null ? request.taskId().toString() : null;
        Map<String, Object> arguments = request.arguments();

        if (taskId == null) {
            return new ToolResultDTO(false, "taskId is required.", Map.of());
        }

        try {
            Object actionsObj = arguments.get("actions");
            if (actionsObj == null) {
                return new ToolResultDTO(false, "The 'actions' argument array is required.", Map.of());
            }

            // Deserialize the raw JSON structure into a typed List<Action> using polymorphic Jackson config
            String json = objectMapper.writeValueAsString(actionsObj);
            List<Action> actions = objectMapper.readValue(json, new TypeReference<List<Action>>() {});

            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append("Action Segment Execution Log:\n");

            for (int i = 0; i < actions.size(); i++) {
                Action action = actions.get(i);
                logBuilder.append(String.format("Step %d: [%s] ", i + 1, action.type()));

                ToolResultDTO stepResult = executeAction(taskId, action);

                logBuilder.append(stepResult.observation()).append("\n");

                if (!stepResult.successful()) {
                    logBuilder.append(String.format(">> HALTING SEGMENT: Step %d failed.", i + 1));
                    return new ToolResultDTO(false, logBuilder.toString(), Map.of());
                }

                // Phase 6: Transition Detection
                if (i < actions.size() - 1) {
                    Action nextAction = actions.get(i + 1);
                    if (nextAction.requiresTarget()) {
                        String nextTargetId = getTargetId(nextAction);
                        if (nextTargetId != null) {
                            boolean stillValid = transitionAnalyzerService.isTargetStillValid(taskId, nextTargetId);
                            if (!stillValid) {
                                logBuilder.append(String.format(">> HALTING SEGMENT: Transition detected. Next target '%s' is no longer valid on screen. Scoped re-observation required.\n", nextTargetId));
                                return new ToolResultDTO(false, logBuilder.toString(), Map.of("transition_detected", true));
                            }
                        }
                    }
                }
            }

            logBuilder.append(">> SEGMENT COMPLETED SUCCESSFULLY.");
            return ToolResultDTO.success(logBuilder.toString());

        } catch (Exception e) {
            return new ToolResultDTO(false, "Failed to parse or execute action segment: " + e.getMessage(), Map.of());
        }
    }

    private ToolResultDTO executeAction(String taskId, Action action) {
        UiElementModel targetElement = null;

        if (action.requiresTarget()) {
            String targetId = getTargetId(action);
            if (targetId == null) {
                return new ToolResultDTO(false, "Action requires a target but none was provided.", Map.of());
            }
            Optional<UiElementModel> opt = elementResolver.resolve(taskId, targetId);
            if (opt.isEmpty()) {
                return new ToolResultDTO(false, "Could not resolve target element: " + targetId, Map.of());
            }
            targetElement = opt.get();
        }

        if (action instanceof ClickAction click) {
            return executorService.executeClick(targetElement, click.button());
        } else if (action instanceof TypeAction typeAction) {
            return executorService.executeType(targetElement, typeAction.text());
        } else if (action instanceof WaitAction wait) {
            return executorService.executeWait(wait.durationMs());
        } else if (action instanceof VerifyAction verify) {
            // For now, simple presence verification
            if (targetElement != null) {
                return ToolResultDTO.success("Verified element presence: " + targetElement.id());
            } else {
                return new ToolResultDTO(false, "Verify failed: element absent", Map.of());
            }
        }

        return new ToolResultDTO(false, "Unknown action implementation.", Map.of());
    }

    private String getTargetId(Action action) {
        if (action instanceof ClickAction a) return a.targetId();
        if (action instanceof TypeAction a) return a.targetId();
        if (action instanceof VerifyAction a) return a.targetId();
        return null;
    }
}

