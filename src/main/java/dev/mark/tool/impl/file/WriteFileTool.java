package dev.mark.tool.impl.file;


import dev.mark.tool.util.FileSystemUtils;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Implementation of the Tool interface that writes text content to files within a sandboxed workspace.
 *
 * - **Purpose**: Safely creates or overwrites files relative to a configured base path, automatically creating parent directories as needed.
 * - **Utility**: Provides file-writing capabilities to the application with built-in path traversal security checks via sandboxing.
 * - **Application Flow**: Acts as an executable component invoked when the system requires file persistence based on external requests.
 * - **Methods and Variables**:
 *   - basePath: Stores the absolute root directory limit for filesystem operations.
 *   - name() & description(): Expose tool metadata for identification.
 *   - parameterSchema(): Defines expected JSON inputs (path and content).
 *   - execute(): Validates arguments, enforces sandbox boundaries, writes the file, and returns operation results.
 * - **Logic and Integration**: Integrates into the core tool execution engine by processing ToolRequestDTO payloads, mapping inputs to secure java.nio operations, and wrapping outcomes in ToolResultDTO responses.
 */

/**
 * Writes content to a file in the sandboxed workspace.
 *
 * <p>The file path is resolved relative to the configured
 * {@code mark.filesystem.base-path}.  Parent directories are created
 * automatically if they do not exist.
 */
@Component
public class WriteFileTool implements Tool {
    private final Path basePath;

    public WriteFileTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "write_file"; }

    @Override public String description() {
        return "Write content to a file. Path is relative to the workspace root. Creates parent directories if needed.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative path of the file to write, e.g. output/result.txt"),
                        "content", Map.of(
                                "type", "string",
                                "description", "Text content to write to the file")),
                "required", List.of("path", "content"));
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawPath = request.arguments().get("path");
        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "write_file requires a non-blank string path argument", Map.of());
        }
        Object rawContent = request.arguments().get("content");
        if (!(rawContent instanceof String content)) {
            return new ToolResultDTO(false, "write_file requires a string content argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content);
            long bytesWritten = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return new ToolResultDTO(true, "Written " + bytesWritten + " bytes to " + pathStr,
                    Map.of("path", pathStr, "bytesWritten", bytesWritten));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to write file: " + e.getMessage(), Map.of());
        }
    }
}
