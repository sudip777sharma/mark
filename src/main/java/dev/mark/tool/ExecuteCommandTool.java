package dev.mark.tool;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Executes a shell command in the sandboxed workspace.
 */
@Component
public class ExecuteCommandTool implements Tool {
    private final Path basePath;

    public ExecuteCommandTool(FileSystemProperties properties) {
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
    public ToolResult execute(ToolRequest request) {
        Object rawCommand = request.arguments().get("command");
        if (!(rawCommand instanceof String command) || command.isBlank()) {
            return new ToolResult(false, "execute_command requires a non-blank string command argument", Map.of());
        }

        try {
            // Determine OS and create the correct process builder
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            pb.directory(basePath.toFile());
            pb.redirectErrorStream(true); // merge stderr into stdout

            Process process = pb.start();

            // Wait up to 30 seconds for the command to finish
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new ToolResult(false, "Command timed out after 30 seconds.", Map.of());
            }

            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int exitCode = process.exitValue();

            if (exitCode == 0) {
                return new ToolResult(true, "Command executed successfully.\nOutput:\n" + output, Map.of("exitCode", exitCode, "output", output));
            } else {
                return new ToolResult(false, "Command failed with exit code " + exitCode + ".\nOutput:\n" + output, Map.of("exitCode", exitCode, "output", output));
            }

        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ToolResult(false, "Failed to execute command: " + e.getMessage(), Map.of());
        }
    }
}
