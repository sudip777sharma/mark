package dev.mark.tool.impl.file;


import dev.mark.tool.util.FileSystemUtils;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * **1. What the class does:**
 * Deletes a specified file or directory recursively within a sandboxed workspace.
 *
 * **2. Why it is useful:**
 * It provides a secure, sandboxed file deletion mechanism that prevents directory traversal attacks, ensuring users or automated agents cannot delete files outside the designated workspace.
 *
 * **3. How it fits in the flow of the application:**
 * As a Spring-managed `@Component` implementing the `Tool` interface, it is registered in the tool registry and executed dynamically by the application's orchestrator when a file deletion action is requested.
 *
 * **4. Methods and variables utility:**
 * - `basePath` (variable): Stores the absolute path of the sandboxed workspace root.
 * - `name()`, `description()`, `parameterSchema()` (methods): Expose the tool's metadata and expected input schema to the calling framework.
 * - `execute(ToolRequestDTO)` (method): Validates the input, resolves the path safely, performs the deletion, and returns the execution status.
 *
 * **5. Logic and integration:**
 * - Resolves the target path against `basePath` using `FileSystemUtils.resolveSandboxed` to enforce security boundaries.
 * - If the path is a directory, it walks the file tree in reverse order to delete children before parents.
 * - If the path is a file, it deletes it directly.
 * - Returns a `ToolResultDTO` indicating success or failure, which the orchestrator uses to update the application state.
 */

/**
 * Deletes a file or directory in the sandboxed workspace.
 */
@Component
public class DeleteFileTool implements Tool {
    private final Path basePath;

    public DeleteFileTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "delete_file"; }

    @Override public String description() {
        return "Delete a file or directory. Path is relative to the workspace root. If a directory is specified, it will be deleted recursively.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative path of the file or directory to delete, e.g. output/result.txt")
                ),
                "required", List.of("path"));
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawPath = request.arguments().get("path");
        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "delete_file requires a non-blank string path argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResultDTO(false, "Path does not exist: " + pathStr, Map.of());
            }

            if (Files.isDirectory(resolved)) {
                try (Stream<Path> walk = Files.walk(resolved)) {
                    walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(java.io.File::delete);
                }
            } else {
                Files.delete(resolved);
            }

            return new ToolResultDTO(true, "Successfully deleted " + pathStr, Map.of("path", pathStr));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to delete path: " + e.getMessage(), Map.of());
        }
    }
}
