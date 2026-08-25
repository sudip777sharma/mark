package dev.mark.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

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

    public WriteFileTool(FileSystemProperties properties) {
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
    public ToolResult execute(ToolRequest request) {
        Object rawPath = request.arguments().get("path");
        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResult(false, "write_file requires a non-blank string path argument", Map.of());
        }
        Object rawContent = request.arguments().get("content");
        if (!(rawContent instanceof String content)) {
            return new ToolResult(false, "write_file requires a string content argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            Path parent = resolved.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content);
            long bytesWritten = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            return new ToolResult(true, "Written " + bytesWritten + " bytes to " + pathStr,
                    Map.of("path", pathStr, "bytesWritten", bytesWritten));
        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to write file: " + e.getMessage(), Map.of());
        }
    }
}
