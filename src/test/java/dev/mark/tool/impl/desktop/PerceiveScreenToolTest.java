package dev.mark.tool.impl.desktop;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;
import dev.mark.vision.service.ScreenshotCacheService;
import dev.mark.vision.service.VisionEngineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PerceiveScreenToolTest {

    private VisionEngineService visionEngine;
    private ScreenshotTool screenshotTool;
    private ScreenshotCacheService screenshotCache;
    private PerceiveScreenTool perceiveTool;

    @BeforeEach
    void setUp() {
        visionEngine = Mockito.mock(VisionEngineService.class);
        screenshotTool = Mockito.mock(ScreenshotTool.class);
        screenshotCache = new ScreenshotCacheService();
        perceiveTool = new PerceiveScreenTool(visionEngine, screenshotTool, screenshotCache);
    }

    @Test
    void testToolContract() {
        assertEquals("perceive_screen", perceiveTool.name());
        assertNotNull(perceiveTool.contract());
        assertEquals("DESKTOP", perceiveTool.contract().capabilityCategory());
    }

    @Test
    void testExecuteWithCachedScreenshot() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        BufferedImage img = new BufferedImage(1536, 864, BufferedImage.TYPE_INT_RGB);
        screenshotCache.update("shot-1", img, screen, "test.png");

        UiElementObservation el = UiElementObservation.builder("el-1", ElementSource.VLM, new BoundingBox(100, 100, 120, 40))
                .text("Search")
                .type("button")
                .confidence(0.95)
                .build();

        UiObservation obs = new UiObservation("shot-1", screen, List.of(el), List.of(), Instant.now().toString());
        when(visionEngine.perceive(any(), any(), any())).thenReturn(obs);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "perceive_screen", Map.of("target", "Search"));
        ToolResultDTO result = perceiveTool.execute(request);

        assertTrue(result.successful());
        assertTrue(result.observation().contains("Vision engine localized 1 UI elements"));
        assertEquals(1, ((List<?>) result.metadata().get("elements")).size());
    }

    @Test
    void testExecuteWithFreshCapture() {
        ScreenDimension screen = new ScreenDimension(1536, 864, 1.0);
        BufferedImage img = new BufferedImage(1536, 864, BufferedImage.TYPE_INT_RGB);

        when(screenshotTool.execute(any())).thenAnswer(invocation -> {
            screenshotCache.update("shot-fresh", img, screen, "fresh.png");
            return new ToolResultDTO(true, "Captured", Map.of());
        });

        UiElementObservation el = UiElementObservation.builder("el-fresh", ElementSource.VLM, new BoundingBox(200, 200, 80, 30))
                .text("Submit")
                .type("button")
                .confidence(0.90)
                .build();

        UiObservation obs = new UiObservation("shot-fresh", screen, List.of(el), List.of(), Instant.now().toString());
        when(visionEngine.perceive(any(), any(), any())).thenReturn(obs);

        ToolRequestDTO request = new ToolRequestDTO(UUID.randomUUID(), "perceive_screen", Map.of("target", "Submit"));
        ToolResultDTO result = perceiveTool.execute(request);

        assertTrue(result.successful());
        assertEquals(1, ((List<?>) result.metadata().get("elements")).size());
    }
}
