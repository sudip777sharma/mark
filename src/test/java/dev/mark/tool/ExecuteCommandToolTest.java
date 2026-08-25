package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteCommandToolTest {

    @TempDir
    Path tempDir;

    @Test
    void executeValidCommand() {
        FileSystemProperties properties = new FileSystemProperties(tempDir.toString());
        ExecuteCommandTool tool = new ExecuteCommandTool(properties);

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String command = isWindows ? "echo hello shell" : "echo \"hello shell\"";

        ToolRequest request = new ToolRequest(UUID.randomUUID(), "execute_command", Map.of("command", command));
        ToolResult result = tool.execute(request);

        assertTrue(result.successful());
        assertTrue(((String) result.metadata().get("output")).contains("hello shell"));
        assertEquals(0, result.metadata().get("exitCode"));
    }

    @Test
    void executeFailingCommand() {
        FileSystemProperties properties = new FileSystemProperties(tempDir.toString());
        ExecuteCommandTool tool = new ExecuteCommandTool(properties);

        // Command that fails
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String command = isWindows ? "dir C:\\this_directory_does_not_exist" : "ls /this_directory_does_not_exist";

        ToolRequest request = new ToolRequest(UUID.randomUUID(), "execute_command", Map.of("command", command));
        ToolResult result = tool.execute(request);

        assertFalse(result.successful());
        assertTrue((Integer) result.metadata().get("exitCode") != 0);
    }
}
