package dev.mark.vision.service;

import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ElementSource;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VisionEngineServiceTest {

    private MirageVlmClient mirageClient;
    private QwenVlmClient qwenClient;
    private VisionEngineService visionEngine;
    private ScreenDimension screen;
    private BufferedImage testImage;

    @BeforeEach
    void setUp() {
        mirageClient = Mockito.mock(MirageVlmClient.class);
        qwenClient = Mockito.mock(QwenVlmClient.class);
        visionEngine = new VisionEngineService(mirageClient, qwenClient);
        screen = new ScreenDimension(1536, 864, 1.0);
        testImage = new BufferedImage(1536, 864, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void testTier1FastPathSuccess() {
        UiElementObservation submitBtn = UiElementObservation.builder("mirage-1", ElementSource.VLM, new BoundingBox(500, 400, 100, 40))
                .text("Submit")
                .type("button")
                .confidence(0.95)
                .build();

        when(mirageClient.detectElements(anyString(), any(), anyString()))
                .thenReturn(List.of(submitBtn));

        UiObservation obs = visionEngine.perceive(testImage, screen, "Submit");

        assertNotNull(obs);
        assertEquals(1, obs.elements().size());
        assertEquals("mirage-1", obs.elements().get(0).elementId());
        assertEquals("Submit", obs.elements().get(0).text());

        // Verify Tier-2 was NOT called (0 ms Qwen latency)
        verify(qwenClient, never()).inspectScreen(anyString(), any(), anyString());
    }

    @Test
    void testTier2EscalationWhenTier1Empty() {
        // Mirage returns empty or unconfident match
        when(mirageClient.detectElements(anyString(), any(), anyString()))
                .thenReturn(List.of());

        UiElementObservation qwenDetected = UiElementObservation.builder("qwen-1", ElementSource.VLM, new BoundingBox(800, 200, 80, 30))
                .text("Special Icon")
                .type("icon")
                .confidence(0.90)
                .build();

        when(qwenClient.inspectScreen(anyString(), any(), anyString()))
                .thenReturn(List.of(qwenDetected));

        UiObservation obs = visionEngine.perceive(testImage, screen, "Special Icon");

        assertNotNull(obs);
        assertEquals(1, obs.elements().size());
        assertEquals("qwen-1", obs.elements().get(0).elementId());

        // Verify both Tier-1 and Tier-2 were queried
        verify(mirageClient, times(1)).detectElements(anyString(), any(), anyString());
        verify(qwenClient, times(1)).inspectScreen(anyString(), any(), anyString());
        assertTrue(obs.uncertainties().stream().anyMatch(u -> u.contains("escalated to Tier-2 Qwen3-VL")));
    }
}
