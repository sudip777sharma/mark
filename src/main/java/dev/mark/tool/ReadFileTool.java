package dev.mark.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads a file from the sandboxed workspace.
 *
 * <p>The file path is resolved relative to the configured
 * {@code mark.filesystem.base-path}.  Files larger than 100 KB are rejected
 * to avoid flooding the LLM context window.
 */
@Component
public class ReadFileTool implements Tool {
    private static final long MAX_SIZE_BYTES = 100 * 1024;
    private final Path basePath;

    public ReadFileTool(FileSystemProperties properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "read_file"; }

    @Override public String description() {
        return "Read the contents of a file. Path is relative to the workspace root.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative path of the file to read, e.g. notes.txt")),
                "required", List.of("path"));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Object raw = request.arguments().get("path");
        if (!(raw instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResult(false, "read_file requires a non-blank string path argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResult(false, "File not found: " + pathStr, Map.of());
            }
            if (!Files.isRegularFile(resolved)) {
                return new ToolResult(false, "Not a regular file: " + pathStr, Map.of());
            }
            long size = Files.size(resolved);
            if (size > MAX_SIZE_BYTES) {
                return new ToolResult(false, "File too large (" + size + " bytes, max " + MAX_SIZE_BYTES + ")", Map.of());
            }
            String content = Files.readString(resolved);
            return new ToolResult(true, "File content (" + size + " bytes):\n" + content,
                    Map.of("path", pathStr, "sizeBytes", size));
        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to read file: " + e.getMessage(), Map.of());
        }
    }
}
