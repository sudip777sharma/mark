package dev.mark.tool.impl.desktop;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OcrTool is a Spring component that reads local image files and encodes them into Base64 for vision-capable LLM analysis.
 *
 * - What it does: Converts a local image file from a specified path into a Base64 string and detects its MIME type.
 * - Why it is useful: Enables the LLM to visually analyze screenshots or UI elements when text inspection is insufficient.
 * - Application Flow: Registered as a Spring bean and dynamically invoked by the agent orchestrator during visual analysis tasks.
 * - Methods and Variables:
 *   - log: Records file reading and processing errors.
 *   - name(): Identifies the tool as "ocr".
 *   - description(): Guides the LLM on tool usage.
 *   - parameterSchema(): Defines the mandatory "image_path" input.
 *   - execute(): Validates the path, reads the file, detects the MIME type, and returns the encoded image payload.
 * - Logic Integration: Resolves the target image path, performs file validation, generates the Base64 encoding, and packages the result for the vision LLM workflow.
 */

/**
 * **OcrTool** is a Spring component that prepares local images for vision-capable LLM analysis.
 *
 * - **What it does:** Reads a local image file from a specified path and encodes it into a Base64 string along with its detected MIME type.
 * - **Why it is useful:** Allows the LLM to "see" and analyze screenshots or on-screen elements when text-based UI inspection fails or lacks detail.
 * - **Application Flow:** Registered as a Spring bean tool, it is dynamically invoked by the agent orchestrator when the LLM decides to perform visual analysis on a screenshot.
 * - **Methods & Variables:**
 *   - `log`: Logs file reading errors.
 *   - `name()`: Returns "ocr" to identify the tool.
 *   - `description()`: Instructs the LLM on when and how to use this tool.
 *   - `parameterSchema()`: Defines the required "image_path" parameter.
 *   - `execute()`: Validates the input path, reads the file, and returns the Base64-encoded image payload.
 * - **Logic & Integration:** Resolves the file path, detects the MIME type (PNG, JPEG, or WEBP), and converts the file to Base64. The resulting payload is returned to the orchestrator, which forwards it to the vision LLM in the subsequent message.
 */

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
    public ToolResultDTO execute(ToolRequestDTO request) {
        String imagePath = (String) request.arguments().get("image_path");
        if (imagePath == null || imagePath.isBlank()) {
            return new ToolResultDTO(false, "ocr requires 'image_path' argument", Map.of());
        }

        try {
            Path path = Path.of(imagePath);
            if (!Files.exists(path)) {
                return new ToolResultDTO(false, "File does not exist: " + imagePath, Map.of());
            }

            String mimeType = "image/png";
            if (imagePath.toLowerCase().endsWith(".jpg") || imagePath.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (imagePath.toLowerCase().endsWith(".webp")) {
                mimeType = "image/webp";
            }

            byte[] bytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);

            return new ToolResultDTO(
                true,
                "Image loaded successfully. The vision model will see this image in the next user message.",
                Map.of("base64Image", base64, "mimeType", mimeType)
            );
        } catch (IOException e) {
            log.error("Failed to read image for OCR: {}", imagePath, e);
            return new ToolResultDTO(false, "Failed to read image: " + e.getMessage(), Map.of());
        }
    }
}
