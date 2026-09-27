package dev.mark.tool.impl.system;



import dev.mark.tool.config.FileSystemPropertiesConfig;
import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * - What it does: Executes OS shell commands within a restricted workspace directory.
 * - Why it is useful: Enables automated terminal actions like builds, tests, and file listing.
 * - Application flow: Implements the Tool interface and integrates as a Spring component for dynamic task execution.
 * - Methods and variables:
 *   - basePath: Defines the secure directory root for process execution.
 *   - execute(): Parses request arguments, builds the OS process, enforces a 30-second timeout, and returns results.
 *   - Metadata methods (name, description, parameterSchema): Expose tool capabilities and required inputs.
 * - Logic integration: Acts as a plug-and-burn execution node responding to incoming ToolRequestDTO instructions.
 */

/**
 * Executes a shell command in the sandboxed workspace.
 */
@Component
public class ExecuteCommandTool implements Tool {
    private final Path basePath;

    public ExecuteCommandTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "execute_command"; }

    @Override public String description() {
        return "Execute a terminal/shell command. Runs in the configured workspace directory. Useful for listing files, running builds, etc.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "The shell command to execute, e.g., 'dir' or 'mvn clean'.")
                ),
                "required", List.of("command")
        );
    }

    @Override
    public ToolResultDTO execute(ToolRequestDTO request) {
        Object rawCommand = request.arguments().get("command");
        if (!(rawCommand instanceof String command) || command.isBlank()) {
            return new ToolResultDTO(false, "execute_command requires a non-blank string command argument", Map.of());
        }

        Path tempOutputFile = null;
        try {
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true);
            
            tempOutputFile = java.nio.file.Files.createTempFile("cmd-out-", ".txt");
            pb.redirectOutput(tempOutputFile.toFile());

            Process process = pb.start();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResultDTO(false, "Command timed out after 30 seconds.", Map.of());
            }

            String output = java.nio.file.Files.readString(tempOutputFile, java.nio.charset.StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode == 0) {
                return new ToolResultDTO(true, "Command executed successfully.\nOutput:\n" + output, Map.of("exitCode", exitCode, "output", output));
            } else {
                return new ToolResultDTO(false, "Command failed with exit code " + exitCode + ".\nOutput:\n" + output, Map.of("exitCode", exitCode, "output", output));
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResultDTO(false, "Failed to execute command: " + e.getMessage(), Map.of());
        } finally {
            if (tempOutputFile != null) {
                try {
                    java.nio.file.Files.deleteIfExists(tempOutputFile);
                } catch (IOException ignored) {}
            }
        }
    }
}

