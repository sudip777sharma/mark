package dev.mark.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Deletes a file or directory in the sandboxed workspace.
 */
@Component
public class DeleteFileTool implements Tool {
    private final Path basePath;

    public DeleteFileTool(FileSystemProperties properties) {
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
    public ToolResult execute(ToolRequest request) {
        Object rawPath = request.arguments().get("path");
        if (!(rawPath instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResult(false, "delete_file requires a non-blank string path argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResult(false, "Path does not exist: " + pathStr, Map.of());
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
            
            return new ToolResult(true, "Successfully deleted " + pathStr, Map.of("path", pathStr));
        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to delete path: " + e.getMessage(), Map.of());
        }
    }
}
