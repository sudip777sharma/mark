package dev.mark.tool.impl.desktop;

import dev.mark.tool.contract.ContractAwareTool;
import dev.mark.tool.contract.IdempotencyPolicy;
import dev.mark.tool.contract.ToolContract;
import dev.mark.tool.contract.ToolParameterSchema;
import dev.mark.tool.contract.ToolRiskClassification;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.model.UiElementObservation;
import dev.mark.vision.model.UiObservation;
import dev.mark.vision.service.ScreenshotCacheService;
import dev.mark.vision.service.VisionEngineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perception tool exposing MARK's dual-tier local vision engine (Mirage VLM + Qwen3-VL 2B) to the agent runtime.
 */
@Component
public class PerceiveScreenTool implements ContractAwareTool {

    private static final Logger log = LoggerFactory.getLogger(PerceiveScreenTool.class);

    private final VisionEngineService visionEngine;
    private final ScreenshotTool screenshotTool;
    private final ScreenshotCacheService screenshotCache;
    private final DesktopAutomationTool desktopTool;
    private final dev.mark.tool.util.BrowserSession browserSession;

    public PerceiveScreenTool(VisionEngineService visionEngine, ScreenshotTool screenshotTool, ScreenshotCacheService screenshotCache) {
        this(visionEngine, screenshotTool, screenshotCache, null, null);
    }

    public PerceiveScreenTool(VisionEngineService visionEngine, ScreenshotTool screenshotTool, ScreenshotCacheService screenshotCache, DesktopAutomationTool desktopTool) {
        this(visionEngine, screenshotTool, screenshotCache, desktopTool, null);
    }

    @Autowired
    public PerceiveScreenTool(VisionEngineService visionEngine, ScreenshotTool screenshotTool, ScreenshotCacheService screenshotCache, DesktopAutomationTool desktopTool, dev.mark.tool.util.BrowserSession browserSession) {
        this.visionEngine = visionEngine;
        this.screenshotTool = screenshotTool;
        this.screenshotCache = screenshotCache;
        this.desktopTool = desktopTool;
        this.browserSession = browserSession;
    }

    @Override
    public String name() {
        return "perceive_screen";
    }

    @Override
    public String description() {
        return "Analyzes the desktop screen using MARK's dual-tier local vision engine (Tier-1 Mirage 22MB and Tier-2 Qwen3-VL 2B). " +
                "Extracts structured UI element bounding boxes, labels, and exact desktop click coordinates for visual navigation.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "target", Map.of("type", "string", "description", "Optional description of the target UI element to find (e.g. 'Search bar', 'Submit button', 'Settings icon')"),
                        "target_app", Map.of("type", "string", "description", "Optional window title or application name to activate/bring to foreground before capture (e.g. 'Chrome', 'Notepad', 'IRCTC')"),
                        "image_path", Map.of("type", "string", "description", "Optional path to existing screenshot. If omitted, captures a fresh screen image.")
                )
        );
    }

    @Override
    public ToolContract contract() {
        return ToolContract.builder(name())
                .description(description())
                .category("DESKTOP")
                .risk(ToolRiskClassification.READ_ONLY)
                .idempotency(IdempotencyPolicy.NATURALLY_IDEMPOTENT)
                .timeoutMs(40_000L)
                .parameter(ToolParameterSchema.optionalString("target", "Target element description", null))
                .parameter(ToolParameterSchema.optionalString("target_app", "Window title or application to activate before capture", null))
                .parameter(ToolParameterSchema.optionalString("image_path", "Path to screenshot image", null))
                .build();
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Map<String, Object> args = request.arguments();
        String target = args.get("target") != null ? args.get("target").toString().trim() : null;
        String targetApp = args.get("target_app") != null ? args.get("target_app").toString().trim() : null;
        String imagePath = args.get("image_path") != null ? args.get("image_path").toString().trim() : null;

        if (targetApp != null && !targetApp.isBlank() && desktopTool != null) {
            log.info("perceive_screen bringing '{}' to foreground before capture", targetApp);
            desktopTool.bringToForeground(targetApp);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (browserSession != null && browserSession.hasActivePage()) {
            log.info("perceive_screen bringing active browser window to foreground before capture");
            browserSession.bringBrowserWindowToForeground();
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        BufferedImage image = null;
        ScreenDimension screen = null;

        if (imagePath != null && !imagePath.isBlank()) {
            File imgFile = new File(imagePath);
            if (imgFile.exists()) {
                try {
                    image = ImageIO.read(imgFile);
                    if (image != null) {
                        screen = new ScreenDimension(image.getWidth(), image.getHeight(), 1.0);
                    }
                } catch (IOException e) {
                    log.warn("Failed to load specified image_path '{}': {}", imagePath, e.getMessage());
                }
            }
        }

        // If no image provided or failed to load, check cache first, then capture fresh via ScreenshotTool
        if (image == null) {
            ScreenshotCacheService.CachedScreenshot cached = screenshotCache.getLatest();
            if (cached != null && cached.image() != null) {
                image = cached.image();
                screen = cached.dimension();
            } else {
                ToolResultDTO shotResult = screenshotTool.execute(request);
                if (shotResult == null || !shotResult.successful()) {
                    return new ToolResultDTO(false, "Failed to capture desktop screenshot for perception: " +
                            (shotResult != null ? shotResult.observation() : "null result"), Map.of());
                }

                cached = screenshotCache.getLatest();
                if (cached != null && cached.image() != null) {
                    image = cached.image();
                    screen = cached.dimension();
                } else {
                    return new ToolResultDTO(false, "Screenshot cache missing captured image buffer", Map.of());
                }
            }
        }

        UiObservation observation = visionEngine.perceive(image, screen, target);

        List<Map<String, Object>> elementDtos = new ArrayList<>();
        for (UiElementObservation el : observation.elements()) {
            Map<String, Object> m = new HashMap<>();
            m.put("element_id", el.elementId());
            m.put("name", el.text());
            m.put("type", el.type());
            m.put("source", el.source().name());
            m.put("confidence", el.confidence());
            m.put("center_x", el.center().x());
            m.put("center_y", el.center().y());
            m.put("bounding_box", Map.of(
                    "x", el.boundingBox().x(),
                    "y", el.boundingBox().y(),
                    "width", el.boundingBox().width(),
                    "height", el.boundingBox().height()
            ));
            elementDtos.add(m);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("screenshot_id", observation.screenshotId());
        metadata.put("screen_width", screen.width());
        metadata.put("screen_height", screen.height());
        metadata.put("elements_count", elementDtos.size());
        metadata.put("elements", elementDtos);
        metadata.put("uncertainties", observation.uncertainties());

        String summary = String.format("Vision engine localized %d UI elements on %dx%d screen (target: '%s')",
                elementDtos.size(), screen.width(), screen.height(), target != null ? target : "all");

        return new ToolResultDTO(true, summary, metadata);
    }
}
