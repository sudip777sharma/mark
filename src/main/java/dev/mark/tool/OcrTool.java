package dev.mark.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OcrTool implements Tool {
    private static final Logger log = LoggerFactory.getLogger(OcrTool.class);

    @Override
    public String name() {
        return "ocr";
    }

    @Override
    public String description() {
        return "Passes an image to the vision-capable LLM to understand what is on screen. "
             + "Use this when inspect_ui fails or lacks detail. Provide the absolute path to a screenshot image.";
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "image_path", Map.of(
                    "type", "string",
                    "description", "Absolute path to the image file to analyze (e.g., from screenshot tool)"
                )
            ),
            "required", List.of("image_path")
        );
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        String imagePath = (String) request.arguments().get("image_path");
        if (imagePath == null || imagePath.isBlank()) {
            return new ToolResult(false, "ocr requires 'image_path' argument", Map.of());
        }

        try {
            Path path = Path.of(imagePath);
            if (!Files.exists(path)) {
                return new ToolResult(false, "File does not exist: " + imagePath, Map.of());
            }

            String mimeType = "image/png";
            if (imagePath.toLowerCase().endsWith(".jpg") || imagePath.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (imagePath.toLowerCase().endsWith(".webp")) {
                mimeType = "image/webp";
            }

            byte[] bytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);

            return new ToolResult(
                true,
                "Image loaded successfully. The vision model will see this image in the next user message.",
                Map.of("base64Image", base64, "mimeType", mimeType)
            );
        } catch (IOException e) {
            log.error("Failed to read image for OCR: {}", imagePath, e);
            return new ToolResult(false, "Failed to read image: " + e.getMessage(), Map.of());
        }
    }
}
