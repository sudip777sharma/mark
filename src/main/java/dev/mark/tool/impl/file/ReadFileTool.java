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
 * **1. What the class does:**
 * Reads the contents of a file from a sandboxed workspace directory.
 *
 * **2. Why it is useful:**
 * It allows LLMs or automated agents to safely inspect local files. It prevents directory traversal attacks through sandboxing and protects the LLM's context window by enforcing a 100 KB file size limit.
 *
 * **3. How it fits in the flow of the application:**
 * It is a Spring-managed @Component implementing the Tool interface. The application's tool execution registry detects it and invokes its execute method when an LLM requests to read a file.
 *
 * **4. Methods and variables and how they are useful:**
 * - MAX_SIZE_BYTES: Constant (100 KB) that prevents reading excessively large files.
 * - basePath: Path variable storing the absolute root of the workspace to anchor all file operations.
 * - name(), description(), parameterSchema(): Define the tool's metadata and expected arguments for the LLM.
 * - execute(): Validates the input path, checks file constraints, reads the content, and returns the result.
 *
 * **5. Logic and how it fits into the overall application logic:**
 * The execute logic resolves the requested path against the basePath using FileSystemUtils.resolveSandboxed to ensure security. It verifies the file exists, is a regular file, and is within the size limit. It then reads the file content into a ToolResultDTO, which is returned to the LLM to guide its next reasoning step.
 */

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

    public ReadFileTool(FileSystemPropertiesConfig properties) {
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
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object raw = request.arguments().get("path");
        if (!(raw instanceof String pathStr) || pathStr.isBlank()) {
            return new ToolResultDTO(false, "read_file requires a non-blank string path argument", Map.of());
        }
        try {
            Path resolved = FileSystemUtils.resolveSandboxed(basePath, pathStr);
            if (!Files.exists(resolved)) {
                return new ToolResultDTO(false, "File not found: " + pathStr, Map.of());
            }
            if (!Files.isRegularFile(resolved)) {
                return new ToolResultDTO(false, "Not a regular file: " + pathStr, Map.of());
            }
            long size = Files.size(resolved);
            if (size > MAX_SIZE_BYTES) {
                return new ToolResultDTO(false, "File too large (" + size + " bytes, max " + MAX_SIZE_BYTES + ")", Map.of());
            }
            String content = Files.readString(resolved);
            return new ToolResultDTO(true, "File content (" + size + " bytes):\n" + content,
                    Map.of("path", pathStr, "sizeBytes", size));
        } catch (IllegalArgumentException e) {
            return new ToolResultDTO(false, e.getMessage(), Map.of());
        } catch (IOException e) {
            return new ToolResultDTO(false, "Failed to read file: " + e.getMessage(), Map.of());
        }
    }
}
