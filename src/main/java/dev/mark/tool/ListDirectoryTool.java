package dev.mark.tool;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lists the immediate children of a directory in the sandboxed workspace.
 *
 * <p>The directory path is resolved relative to the configured
 * {@code mark.filesystem.base-path}.  Defaults to the workspace root
 * when no path is provided or when {@code "."} is given.
 */
@Component
public class ListDirectoryTool implements Tool {
    private final Path basePath;

    public ListDirectoryTool(FileSystemProperties properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "list_directory"; }

    @Override public String description() {
        return "List files and directories in a workspace directory. Path is relative to the workspace root; defaults to root.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "Relative directory path, e.g. data/. Defaults to workspace root.")));
    }

    @Override
    public ToolResult execute(ToolRequest request) {
        Object raw = request.arguments().get("path");
        String pathStr = (raw instanceof String s && !s.isBlank()) ? s : ".";
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResult(false, "Directory not found: " + pathStr, Map.of());
            }
            if (!Files.isDirectory(resolved)) {
                return new ToolResult(false, "Not a directory: " + pathStr, Map.of());
            }
            List<String> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(resolved)) {
                for (Path entry : stream) {
                    String name = entry.getFileName().toString();
                    if (Files.isDirectory(entry)) {
                        entries.add(name + "/ (directory)");
                    } else {
                        long size = Files.size(entry);
                        entries.add(name + " (file, " + size + " bytes)");
                    }
                }
            }
            entries.sort(String.CASE_INSENSITIVE_ORDER);
            if (entries.isEmpty()) {
                return ToolResult.success("Directory is empty: " + pathStr);
            }
            return new ToolResult(true, "Contents of " + pathStr + ":\n" + String.join("\n", entries),
                    Map.of("path", pathStr, "entryCount", entries.size()));
        } catch (IllegalArgumentException e) {
            return new ToolResult(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResult(false, "Failed to list directory: " + e.getMessage(), Map.of());
        }
    }
}
