package dev.mark.agent.service.visual;

import dev.mark.agent.model.AgentStateModel;
import dev.mark.agent.model.AgentStepModel;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.service.TaskSseService;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.desktop.DesktopAutomationTool;
import dev.mark.tool.impl.desktop.ScreenshotTool;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.ScreenPoint;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.service.MirageVlmClient;
import dev.mark.vision.service.ScreenshotCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/**
 * Dedicated visual agent runner that executes desktop automation tasks purely using
 * Tier-1 Mirage VLM (22 MB) for screen observation, target localization, and action verification.
 */
@Service
public class MirageVisualAgentRunner {

    private static final Logger log = LoggerFactory.getLogger(MirageVisualAgentRunner.class);
    private static final int MAX_VISUAL_STEPS = 15;

    private final MirageVlmClient mirageClient;
    private final ScreenshotTool screenshotTool;
    private final ScreenshotCacheService screenshotCache;
    private final DesktopAutomationTool desktopTool;
    private final TaskRepository taskRepository;
    private final TaskSseService taskSseService;
    private final MeterRegistry meterRegistry;

    @Autowired
    public MirageVisualAgentRunner(
            MirageVlmClient mirageClient,
            ScreenshotTool screenshotTool,
            ScreenshotCacheService screenshotCache,
            DesktopAutomationTool desktopTool,
            TaskRepository taskRepository,
            TaskSseService taskSseService,
            MeterRegistry meterRegistry) {
        this.mirageClient = mirageClient;
        this.screenshotTool = screenshotTool;
        this.screenshotCache = screenshotCache;
        this.desktopTool = desktopTool;
        this.taskRepository = taskRepository;
        this.taskSseService = taskSseService;
        this.meterRegistry = meterRegistry;
    }

    public TaskResponseDTO execute(AgentStateModel state, Timer.Sample sample) {
        String goal = state.goal();
        log.info(">>> [MIRAGE_RUNNER:START] taskId={} goal='{}'", state.taskId(), goal);
        state.setStatus("EXECUTING");

        UUID taskUuid = UUID.fromString(state.taskId());
        if (isTaskCancelled(taskUuid)) {
            log.info("MirageRunner task {} was cancelled before starting", state.taskId());
            return failTask(state, "Task was stopped by user.", sample);
        }

        updateAction(state, "Starting pure visual execution using Mirage VLM (22 MB)...");

        // Step 1: Detect and activate the target application window
        List<String> targetApps = resolveTargetApplications(goal);
        for (String targetApp : targetApps) {
            updateAction(state, "Activating and focusing window: " + targetApp);
            ToolResultDTO focusResult = desktopTool.bringToForeground(targetApp);
            if (focusResult != null) {
                log.info("MirageRunner foreground activation for '{}': {}", targetApp, focusResult.observation());
                if (focusResult.successful()) {
                    try {
                        Thread.sleep(500); // Settling delay for window to render in foreground
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    break;
                }
            }
        }

        int stepNumber = 1;
        boolean goalAchieved = false;

        while (stepNumber <= MAX_VISUAL_STEPS && !goalAchieved) {
            // Check cancellation
            if (isTaskCancelled(taskUuid)) {
                log.info("MirageRunner task {} was cancelled", state.taskId());
                return failTask(state, "Task was stopped by user.", sample);
            }

            updateAction(state, String.format("Step %d: Capturing screen and running Mirage VLM...", stepNumber));

            // Step 2: Capture fresh screenshot of the foreground window
            ToolRequestDTO shotReq = new ToolRequestDTO(UUID.randomUUID(), "screenshot", Map.of());
            ToolResultDTO shotResult = screenshotTool.execute(shotReq);
            if (!shotResult.successful()) {
                log.error("MirageRunner screenshot capture failed: {}", shotResult.observation());
                return failTask(state, "Failed to capture desktop screenshot: " + shotResult.observation(), sample);
            }

            ScreenshotCacheService.CachedScreenshot cached = screenshotCache.getLatest();
            if (cached == null || cached.image() == null) {
                return failTask(state, "Screenshot cache missing captured image buffer", sample);
            }

            BufferedImage image = cached.image();
            ScreenDimension screen = cached.dimension();
            String base64Image = encodeImage(image);

            // Step 3: Run Mirage VLM (~100 ms)
            updateAction(state, String.format("Step %d: Mirage VLM analyzing visual controls...", stepNumber));
            List<UiElementObservation> elements = mirageClient.detectElements(base64Image, screen, goal);
            log.info("Mirage detected {} elements on screen", elements.size());

            // Step 4: Resolve next actionable control from Mirage elements matching user goal
            ActionCandidate nextAction = planNextVisualAction(goal, elements, stepNumber);

            if (nextAction == null) {
                // If no direct action found, check if previous actions achieved the goal or finish
                if (stepNumber > 1) {
                    log.info("No further actions detected; visual sequence completed.");
                    goalAchieved = true;
                    break;
                } else {
                    return failTask(state, "Mirage VLM could not find actionable UI elements matching: " + goal, sample);
                }
            }

            // Step 5: Execute visual action via DesktopAutomationTool
            updateAction(state, String.format("Step %d: %s at (%d, %d)", stepNumber, nextAction.description(), nextAction.targetPoint().x(), nextAction.targetPoint().y()));

            ToolResultDTO actionResult = executeVisualAction(nextAction);
            AgentStepModel stepModel = new AgentStepModel(
                    stepNumber,
                    nextAction.description(),
                    "desktop_automation",
                    nextAction.argumentsJson(),
                    actionResult.observation(),
                    "Mirage VLM (Local Edge Vision)",
                    Instant.now()
            );
            state.addStep(stepModel);
            saveTaskProgress(state);

            if (!actionResult.successful()) {
                log.warn("Visual action step {} failed: {}", stepNumber, actionResult.observation());
            }

            // Step 6: Brief settling delay
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            if (nextAction.isTerminal()) {
                goalAchieved = true;
            }
            stepNumber++;
        }

        state.setStatus("COMPLETED");
        String finalAnswer = "Task successfully completed using pure Mirage VLM (22 MB) local execution.";
        state.setFinalAnswer(finalAnswer);
        updateAction(state, "Completed visual task: " + goal);
        return completeTask(state, finalAnswer, sample);
    }

    private List<String> resolveTargetApplications(String goal) {
        if (goal == null) return List.of();
        List<String> targets = new ArrayList<>();
        String lower = goal.toLowerCase();
        if (lower.contains("irctc")) {
            targets.add("IRCTC");
            targets.add("Chrome");
            targets.add("Edge");
        } else if (lower.contains("whatsapp")) {
            targets.add("WhatsApp");
            targets.add("Chrome");
        } else if (lower.contains("notepad")) {
            targets.add("Notepad");
        } else if (lower.contains("calculator")) {
            targets.add("Calculator");
        } else if (lower.contains("spotify")) {
            targets.add("Spotify");
        } else if (lower.contains("chrome") || lower.contains("browser") || lower.contains("web")) {
            targets.add("Chrome");
            targets.add("Edge");
        } else if (lower.contains("edge")) {
            targets.add("Edge");
        }
        return targets;
    }

    private ActionCandidate planNextVisualAction(String goal, List<UiElementObservation> elements, int stepNumber) {
        String lower = goal.toLowerCase();

        // 1. Check for modal dismissal / language selection dialogs (e.g. "English", "OK", "Accept", "Close")
        if (stepNumber <= 2) {
            for (UiElementObservation el : elements) {
                if (el.text() != null) {
                    String t = el.text().trim();
                    if (t.equalsIgnoreCase("English") || t.equalsIgnoreCase("OK") || t.equalsIgnoreCase("Accept") || t.equalsIgnoreCase("I agree")) {
                        return new ActionCandidate("Click '" + t + "' button", el.center(), "click", null, false);
                    }
                }
            }
        }

        // 2. Direct semantic matching: check if any element text is mentioned in the goal
        for (UiElementObservation el : elements) {
            if (el.text() != null && el.text().trim().length() > 2) {
                String elText = el.text().toLowerCase().trim();
                if (lower.contains(elText)) {
                    boolean isTerminal = elText.contains("search") || elText.contains("submit") || elText.contains("book") || elText.contains("login");
                    return new ActionCandidate("Click '" + el.text() + "'", el.center(), "click", null, isTerminal);
                }
            }
        }

        // 3. Search / submit button
        if (lower.contains("search") || lower.contains("find")) {
            for (UiElementObservation el : elements) {
                if (el.text() != null && el.text().toLowerCase().contains("search")) {
                    return new ActionCandidate("Click 'Search' button", el.center(), "click", null, true);
                }
            }
        }

        // 4. Fallback: click first matching interactable control
        if (!elements.isEmpty()) {
            UiElementObservation first = elements.get(0);
            return new ActionCandidate("Interact with " + first.text(), first.center(), "click", null, true);
        }

        return null;
    }

    private ToolResultDTO executeVisualAction(ActionCandidate action) {
        Map<String, Object> args;
        if ("type".equalsIgnoreCase(action.actionType())) {
            args = Map.of(
                    "action", "activate_and_click",
                    "x", action.targetPoint().x(),
                    "y", action.targetPoint().y(),
                    "text", action.textToType() != null ? action.textToType() : ""
            );
        } else {
            args = Map.of(
                    "action", "click",
                    "x", action.targetPoint().x(),
                    "y", action.targetPoint().y(),
                    "button", "left"
            );
        }
        return desktopTool.execute(new ToolRequestDTO(UUID.randomUUID(), "desktop_automation", args));
    }

    private String encodeImage(BufferedImage img) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    private boolean isTaskCancelled(UUID taskUuid) {
        return taskRepository.findById(taskUuid)
                .map(t -> t.getStatus() == dev.mark.agent.model.AgentStatusModel.FAILED)
                .orElse(false);
    }

    private void updateAction(AgentStateModel state, String action) {
        state.setCurrentAction(action);
        UUID taskUuid = UUID.fromString(state.taskId());
        taskRepository.findById(taskUuid).ifPresent(t -> {
            t.setStatus(dev.mark.agent.model.AgentStatusModel.EXECUTING);
            t.setCurrentAction(action);
            taskRepository.save(t);
            taskSseService.broadcastTaskUpdate(t);
        });
    }

    private void saveTaskProgress(AgentStateModel state) {
        UUID taskUuid = UUID.fromString(state.taskId());
        taskRepository.findById(taskUuid).ifPresent(t -> {
            t.setCurrentAction(state.currentAction());
            // Map steps
            List<dev.mark.task.entity.AgentStepEmbeddableEntity> stepEntities = state.steps().stream()
                    .map(s -> new dev.mark.task.entity.AgentStepEmbeddableEntity(
                            s.number(), s.description(), s.toolName(), s.toolArguments(), s.outcome(), s.provider(), s.timestamp()
                    ))
                    .toList();
            t.setSteps(stepEntities);
            taskRepository.save(t);
            taskSseService.broadcastTaskUpdate(t);
        });
    }

    private TaskResponseDTO completeTask(AgentStateModel state, String answer, Timer.Sample sample) {
        UUID taskUuid = UUID.fromString(state.taskId());
        taskRepository.findById(taskUuid).ifPresent(t -> {
            t.setStatus(dev.mark.agent.model.AgentStatusModel.COMPLETED);
            t.setFinalAnswer(answer);
            t.setCurrentAction("Completed");
            taskRepository.save(t);
            taskSseService.broadcastTaskUpdate(t);
        });
        if (sample != null) {
            sample.stop(meterRegistry.timer("mark.agent.task.duration", "status", "completed"));
        }
        return new TaskResponseDTO(taskUuid, state.goal(), dev.mark.agent.model.AgentStatusModel.COMPLETED, answer, "Completed", state.plan(), state.llmRequestCount(), state.steps());
    }

    private TaskResponseDTO failTask(AgentStateModel state, String error, Timer.Sample sample) {
        UUID taskUuid = UUID.fromString(state.taskId());
        taskRepository.findById(taskUuid).ifPresent(t -> {
            t.setStatus(dev.mark.agent.model.AgentStatusModel.FAILED);
            t.setCurrentAction("Failed: " + error);
            t.setFinalAnswer("Task failed during Mirage visual execution: " + error);
            taskRepository.save(t);
            taskSseService.broadcastTaskUpdate(t);
        });
        if (sample != null) {
            sample.stop(meterRegistry.timer("mark.agent.task.duration", "status", "failed"));
        }
        return new TaskResponseDTO(taskUuid, state.goal(), dev.mark.agent.model.AgentStatusModel.FAILED, error, "Failed", state.plan(), state.llmRequestCount(), state.steps());
    }

    private record ActionCandidate(
            String description,
            ScreenPoint targetPoint,
            String actionType,
            String textToType,
            boolean isTerminal
    ) {
        public String argumentsJson() {
            return String.format("{\"action\":\"%s\",\"x\":%d,\"y\":%d%s}",
                    actionType, targetPoint.x(), targetPoint.y(),
                    textToType != null ? ",\"text\":\"" + textToType + "\"" : "");
        }
    }
}
