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
 * Executes shell commands and launches desktop applications.
 *
 * <p>On Windows, direct application launches (e.g. 'notepad', 'calc', 'start')
 * are executed asynchronously via Windows shell 'start' so that GUI applications
 * open immediately without blocking or timing out after 30 seconds.
 */
@Component
public class ExecuteCommandTool implements Tool {
    private final Path basePath;

    public ExecuteCommandTool(FileSystemPropertiesConfig properties) {
        this.basePath = Path.of(properties.basePath()).toAbsolutePath();
    }

    @Override public String name() { return "execute_command"; }

    @Override public String description() {
        return "Execute a terminal/shell command or launch a Windows application (e.g. 'notepad', 'calc'). Runs in workspace directory.";
    }

    @Override public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "The shell command or application to execute, e.g., 'notepad', 'dir', or 'mvn test'.")
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
            String actualCommand = command.trim();

            if (isWindows) {
                String lower = actualCommand.toLowerCase();
                // Detach GUI applications so they don't block cmd.exe and trigger 30s timeouts
                if (lower.startsWith("notepad") || lower.startsWith("calc") || lower.startsWith("mspaint")
                        || lower.startsWith("code") || lower.startsWith("explorer") || lower.startsWith("start ")) {
                    if (!lower.startsWith("start ")) {
                        actualCommand = "start \"\" " + actualCommand;
                    } else if (!lower.startsWith("start \"\"")) {
                        actualCommand = "start \"\" " + actualCommand.substring(6).trim();
                    }
                }
            }

            ProcessBuilder pb;
            if (isWindows) {
                pb = new ProcessBuilder("cmd.exe", "/c", actualCommand);
            } else {
                pb = new ProcessBuilder("sh", "-c", actualCommand);
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
                String successMsg = output.isBlank() 
                    ? "Command executed successfully: " + command 
                    : "Command executed successfully.\nOutput:\n" + output;
                return new ToolResultDTO(true, successMsg, Map.of("exitCode", exitCode, "output", output));
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
