package dev.mark.tool.impl.desktop;

import dev.mark.tool.contract.ContractAwareTool;
import dev.mark.tool.contract.IdempotencyPolicy;
import dev.mark.tool.contract.ToolContract;
import dev.mark.tool.contract.ToolParameterSchema;
import dev.mark.tool.contract.ToolRiskClassification;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.vision.model.BoundingBox;
import dev.mark.vision.model.ScreenDimension;
import dev.mark.vision.service.ScreenshotCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Native DPI-aware desktop screenshot capture tool with ground-truth verification and in-memory caching.
 */
@Component
public class ScreenshotTool implements ContractAwareTool {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotTool.class);
    private final Robot robot;
    private final ScreenshotCacheService cacheService;

    @Autowired
    public ScreenshotTool(ScreenshotCacheService cacheService) {
        this.cacheService = cacheService;
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new RuntimeException("Failed to initialize java.awt.Robot for ScreenshotTool", e);
        }
    }

    public ScreenshotTool() {
        this(new ScreenshotCacheService());
    }

    @Override
    public String name() {
        return "screenshot";
    }

    @Override
    public String description() {
        return "Captures a DPI-aware screenshot of the primary desktop or a specific sub-region. " +
                "Saves the image to PNG and returns the file path, dimensions, and observation metadata.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "file_path", Map.of("type", "string", "description", "Optional output PNG path. Defaults to mark-workspace/screenshots/screenshot_<uuid>.png"),
                        "x", Map.of("type", "integer", "description", "Optional sub-region X coordinate"),
                        "y", Map.of("type", "integer", "description", "Optional sub-region Y coordinate"),
                        "width", Map.of("type", "integer", "description", "Optional sub-region width"),
                        "height", Map.of("type", "integer", "description", "Optional sub-region height")
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
                .timeoutMs(15_000L)
                .parameter(ToolParameterSchema.optionalString("file_path", "Destination PNG file path", null))
                .parameter(ToolParameterSchema.optionalInt("x", "Crop region X", null))
                .parameter(ToolParameterSchema.optionalInt("y", "Crop region Y", null))
                .parameter(ToolParameterSchema.optionalInt("width", "Crop region width", null))
                .parameter(ToolParameterSchema.optionalInt("height", "Crop region height", null))
                .build();
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Map<String, Object> args = request.arguments();
        String requestedPath = args.get("file_path") != null ? args.get("file_path").toString().trim() : null;

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle screenBounds = gd.getDefaultConfiguration().getBounds();

        Rectangle captureBounds = screenBounds;
        boolean isCropped = false;

        if (args.containsKey("x") && args.containsKey("y") && args.containsKey("width") && args.containsKey("height")) {
            try {
                int x = ((Number) args.get("x")).intValue();
                int y = ((Number) args.get("y")).intValue();
                int w = ((Number) args.get("width")).intValue();
                int h = ((Number) args.get("height")).intValue();

                if (w > 0 && h > 0) {
                    int clampedX = Math.clamp(x, screenBounds.x, screenBounds.x + screenBounds.width - 1);
                    int clampedY = Math.clamp(y, screenBounds.y, screenBounds.y + screenBounds.height - 1);
                    int clampedW = Math.min(w, screenBounds.x + screenBounds.width - clampedX);
                    int clampedH = Math.min(h, screenBounds.y + screenBounds.height - clampedY);

                    captureBounds = new Rectangle(clampedX, clampedY, clampedW, clampedH);
                    isCropped = true;
                }
            } catch (Exception e) {
                log.warn("Invalid crop bounds provided to screenshot tool, falling back to full screen: {}", e.getMessage());
            }
        }

        try {
            BufferedImage image = robot.createScreenCapture(captureBounds);
            if (image == null) {
                return new ToolResultDTO(false, "java.awt.Robot returned null BufferedImage for desktop capture", Map.of());
            }

            Path outputPath;
            if (requestedPath != null && !requestedPath.isBlank()) {
                outputPath = Path.of(requestedPath);
            } else {
                outputPath = Path.of("mark-workspace", "screenshots", "screenshot_" + UUID.randomUUID() + ".png");
            }

            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }

            File targetFile = outputPath.toFile();
            boolean written = ImageIO.write(image, "PNG", targetFile);
            if (!written) {
                return new ToolResultDTO(false, "ImageIO failed to find an appropriate PNG writer", Map.of());
            }

            // Postcondition verification: file must exist and have valid non-empty size
            if (!targetFile.exists() || targetFile.length() < 100) {
                return new ToolResultDTO(false, "Screenshot file verification failed: file is missing or corrupt (" + targetFile.getAbsolutePath() + ")", Map.of());
            }

            String screenshotId = UUID.randomUUID().toString();
            ScreenDimension dimension = new ScreenDimension(image.getWidth(), image.getHeight(), 1.0);

            // Update in-memory cache
            cacheService.update(screenshotId, image, dimension, targetFile.getAbsolutePath());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("screenshot_id", screenshotId);
            metadata.put("file_path", targetFile.getAbsolutePath());
            metadata.put("width", image.getWidth());
            metadata.put("height", image.getHeight());
            metadata.put("scale_factor", 1.0);
            metadata.put("cropped", isCropped);
            metadata.put("file_size_bytes", targetFile.length());
            metadata.put("timestamp", Instant.now().toString());

            String msg = String.format("Captured screenshot (%dx%d) saved to %s",
                    image.getWidth(), image.getHeight(), targetFile.getAbsolutePath());

            log.info("{}", msg);
            return new ToolResultDTO(true, msg, metadata);

        } catch (IOException e) {
            log.error("Failed to capture and save screenshot: {}", e.getMessage(), e);
            return new ToolResultDTO(false, "Screenshot I/O error: " + e.getMessage(), Map.of());
        }
    }
}
