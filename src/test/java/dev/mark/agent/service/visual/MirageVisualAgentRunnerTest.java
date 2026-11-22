package dev.mark.agent.service.visual;

import dev.mark.agent.model.AgentStateModel;
import dev.mark.agent.model.AgentStatusModel;
import dev.mark.task.dto.TaskResponseDTO;
import dev.mark.task.entity.TaskEntity;
import dev.mark.task.repository.TaskRepository;
import dev.mark.task.service.TaskSseService;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.desktop.DesktopAutomationTool;
import dev.mark.tool.impl.desktop.ScreenshotTool;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.service.MirageVlmClient;
import dev.mark.vision.service.ScreenshotCacheService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MirageVisualAgentRunnerTest {

    private MirageVlmClient mirageClient;
    private ScreenshotTool screenshotTool;
    private ScreenshotCacheService screenshotCache;
    private DesktopAutomationTool desktopTool;
    private TaskRepository taskRepository;
    private TaskSseService taskSseService;
    private MeterRegistry meterRegistry;
    private MirageVisualAgentRunner runner;

    private UUID taskId;
    private TaskEntity taskEntity;
    private BufferedImage sampleImage;
    private ScreenDimension dimension;

    @BeforeEach
    void setUp() {
        mirageClient = mock(MirageVlmClient.class);
        screenshotTool = mock(ScreenshotTool.class);
        screenshotCache = mock(ScreenshotCacheService.class);
        desktopTool = mock(DesktopAutomationTool.class);
        taskRepository = mock(TaskRepository.class);
        taskSseService = mock(TaskSseService.class);
        meterRegistry = new SimpleMeterRegistry();

        runner = new MirageVisualAgentRunner(
                mirageClient,
                screenshotTool,
                screenshotCache,
                desktopTool,
                taskRepository,
                taskSseService,
                meterRegistry
        );

        taskId = UUID.randomUUID();
        taskEntity = new TaskEntity(taskId, "Book ticket on IRCTC", AgentStatusModel.PLANNING, null, Instant.now());
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(taskEntity));

        sampleImage = new BufferedImage(1536, 864, BufferedImage.TYPE_INT_RGB);
        dimension = new ScreenDimension(1536, 864, 1.0);
    }

    @Test
    void testExecuteSuccessWithAppActivationAndMirageVisualAction() {
        String goal = "Search trains on IRCTC";
        AgentStateModel state = new AgentStateModel(taskId.toString(), goal, null);
        Timer.Sample sample = Timer.start(meterRegistry);

        // Mock window activation
        when(desktopTool.bringToForeground("IRCTC"))
                .thenReturn(ToolResultDTO.success("Activated: IRCTC Next Generation"));

        // Mock screenshot
        when(screenshotTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(ToolResultDTO.success("Captured screen: 1536x864"));
        when(screenshotCache.getLatest())
                .thenReturn(new ScreenshotCacheService.CachedScreenshot("shot-1", sampleImage, dimension, "shot.png", Instant.now()));

        // Mock Mirage element detection
        UiElementObservation searchBtn = UiElementObservation.builder("mirage-1", ElementSource.VLM, new BoundingBox(700, 450, 120, 40))
                .text("Search Trains")
                .type("button")
                .confidence(0.96)
                .build();
        when(mirageClient.detectElements(anyString(), any(ScreenDimension.class), anyString()))
                .thenReturn(List.of(searchBtn));

        // Mock desktop action
        when(desktopTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(ToolResultDTO.success("Left click executed at (760, 470)"));

        TaskResponseDTO response = runner.execute(state, sample);

        assertNotNull(response);
        assertEquals(AgentStatusModel.COMPLETED, response.status());
        assertTrue(response.finalAnswer().contains("Mirage VLM"));

        // Verify window activation was called for IRCTC
        verify(desktopTool).bringToForeground("IRCTC");

        // Verify screenshot was taken
        verify(screenshotTool).execute(any(ToolRequestDTO.class));

        // Verify Mirage VLM was queried
        verify(mirageClient).detectElements(anyString(), eq(dimension), eq(goal));

        // Verify action executed at center of search button (700+60=760, 450+20=470)
        verify(desktopTool).execute(argThat(req -> {
            Map<String, Object> params = req.arguments();
            return "desktop_automation".equals(req.action())
                    && Integer.valueOf(760).equals(params.get("x"))
                    && Integer.valueOf(470).equals(params.get("y"));
        }));
    }

    @Test
    void testWindowActivationFallbackToChromeWhenTargetWindowNotFound() {
        String goal = "Book ticket on IRCTC";
        AgentStateModel state = new AgentStateModel(taskId.toString(), goal, null);
        Timer.Sample sample = Timer.start(meterRegistry);

        // IRCTC not found, but Chrome is found
        when(desktopTool.bringToForeground("IRCTC"))
                .thenReturn(new ToolResultDTO(false, "ERROR: Window not found", Map.of()));
        when(desktopTool.bringToForeground("Chrome"))
                .thenReturn(ToolResultDTO.success("Activated: Google Chrome"));

        when(screenshotTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(ToolResultDTO.success("Captured screen"));
        when(screenshotCache.getLatest())
                .thenReturn(new ScreenshotCacheService.CachedScreenshot("shot-1", sampleImage, dimension, "shot.png", Instant.now()));

        UiElementObservation element = UiElementObservation.builder("mirage-1", ElementSource.VLM, new BoundingBox(500, 300, 80, 30))
                .text("Search")
                .type("button")
                .confidence(0.92)
                .build();
        when(mirageClient.detectElements(anyString(), any(), anyString()))
                .thenReturn(List.of(element));
        when(desktopTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(ToolResultDTO.success("Clicked"));

        TaskResponseDTO response = runner.execute(state, sample);

        assertEquals(AgentStatusModel.COMPLETED, response.status());
        verify(desktopTool).bringToForeground("IRCTC");
        verify(desktopTool).bringToForeground("Chrome");
    }

    @Test
    void testFailsGracefullyWhenScreenshotFails() {
        String goal = "Interact with application";
        AgentStateModel state = new AgentStateModel(taskId.toString(), goal, null);
        Timer.Sample sample = Timer.start(meterRegistry);

        when(desktopTool.bringToForeground(anyString())).thenReturn(ToolResultDTO.success("Activated"));
        when(screenshotTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(new ToolResultDTO(false, "Display device unavailable", Map.of()));

        TaskResponseDTO response = runner.execute(state, sample);

        assertNotNull(response);
        assertEquals(AgentStatusModel.FAILED, response.status());
        assertTrue(response.finalAnswer().contains("Failed to capture desktop screenshot"));
    }

    @Test
    void testFailsGracefullyWhenNoElementsDetectedByMirage() {
        String goal = "Click hidden element";
        AgentStateModel state = new AgentStateModel(taskId.toString(), goal, null);
        Timer.Sample sample = Timer.start(meterRegistry);

        when(desktopTool.bringToForeground(anyString())).thenReturn(ToolResultDTO.success("Activated"));
        when(screenshotTool.execute(any(ToolRequestDTO.class))).thenReturn(ToolResultDTO.success("Captured"));
        when(screenshotCache.getLatest())
                .thenReturn(new ScreenshotCacheService.CachedScreenshot("shot-1", sampleImage, dimension, "shot.png", Instant.now()));
        when(mirageClient.detectElements(anyString(), any(), anyString()))
                .thenReturn(List.of()); // Mirage detects nothing

        TaskResponseDTO response = runner.execute(state, sample);

        assertNotNull(response);
        assertEquals(AgentStatusModel.FAILED, response.status());
        assertTrue(response.finalAnswer().contains("Mirage VLM could not find actionable UI elements"));
    }

    @Test
    void testAbortsWhenTaskCancelledByUser() {
        String goal = "Search trains on IRCTC";
        AgentStateModel state = new AgentStateModel(taskId.toString(), goal, null);
        Timer.Sample sample = Timer.start(meterRegistry);

        // Task marked FAILED in repo before/during execution (user clicked Stop)
        TaskEntity cancelledEntity = new TaskEntity(taskId, goal, AgentStatusModel.FAILED, "Stopped", Instant.now());
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(cancelledEntity));

        TaskResponseDTO response = runner.execute(state, sample);

        assertNotNull(response);
        assertEquals(AgentStatusModel.FAILED, response.status());
        assertTrue(response.finalAnswer().contains("stopped by user"));
    }
}
