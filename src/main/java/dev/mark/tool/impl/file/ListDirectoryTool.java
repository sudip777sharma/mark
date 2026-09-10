package dev.mark.tool.impl.file;


import dev.mark.tool.util.FileSystemUtils;
import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Lists the immediate files and directories inside a specified directory within a sandboxed workspace.
 *
 * - **What it does:** Lists immediate children of a target directory path relative to the workspace root.
 * - **Why it is useful:** Provides secure workspace inspection while preventing directory traversal attacks.
 * - **Application Flow:** Acts as a Spring Component implementing the Tool interface, invoked dynamically by the tool execution framework.
 * - **Methods and Variables:**
 *   - basePath: Absolute root path of the sandboxed workspace.
 *   - name, description, parameterSchema: Provide tool metadata and expected path arguments.
 *   - execute: Processes requests, enforces security, reads directory streams, and returns results.
 * - **Logic and Integration:** Resolves and validates paths using FileSystemUtils, reads directory contents with Files.newDirectoryStream, and packages sorted entries into a ToolResultDTO for unified execution flow.
 */

/**
 * **What it does:**
 * Lists the immediate files and directories inside a specified directory within a sandboxed workspace.
 *
 * **Why it is useful:**
 * Provides a secure way to inspect workspace contents. It prevents directory traversal attacks by sandboxing all path resolutions relative to a configured base directory.
 *
 * **How it fits in the flow:**
 * Implements the Tool interface as a Spring @Component. It is registered in the application's tool execution framework and invoked dynamically when a directory listing is requested.
 *
 * **Methods and Variables:**
 * - basePath: Path variable storing the absolute root of the sandboxed workspace.
 * - name(), description(), parameterSchema(): Metadata methods that describe the tool and its expected "path" argument to the system.
 * - execute(ToolRequestDTO): The main execution method that processes the input path, performs safety checks, reads the directory, and returns the formatted results.
 *
 * **Logic and Application Integration:**
 * - Resolves the input path against the basePath using FileSystemUtils.resolveSandboxed to enforce security boundaries.
 * - Validates that the target path exists and is a directory.
 * - Reads directory contents using Files.newDirectoryStream, formatting entries with file sizes or directory markers.
 * - Sorts and packages the results into a ToolResultDTO, integrating seamlessly with the application's unified tool execution flow.
 */

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

    public ListDirectoryTool(FileSystemPropertiesConfig properties) {
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
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object raw = request.arguments().get("path");
        String pathStr = (raw instanceof String s && !s.isBlank()) ? s : ".";
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResultDTO(false, "Directory not found: " + pathStr, Map.of());
            }
            if (!Files.isDirectory(resolved)) {
                return new ToolResultDTO(false, "Not a directory: " + pathStr, Map.of());
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
                return ToolResultDTO.success("Directory is empty: " + pathStr);
            }
            return new ToolResultDTO(true, "Contents of " + pathStr + ":\n" + String.join("\n", entries),
                    Map.of("path", pathStr, "entryCount", entries.size()));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to list directory: " + e.getMessage(), Map.of());
        }
    }
}
