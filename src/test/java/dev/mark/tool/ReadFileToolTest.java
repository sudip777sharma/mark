package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReadFileToolTest {
    @TempDir Path sandbox;
    private ReadFileTool tool;

    @BeforeEach void setUp() {
        tool = new ReadFileTool(new FileSystemProperties(sandbox.toString()));
    }

    private ToolResult read(String path) {
        return tool.execute(new ToolRequest(UUID.randomUUID(), "read_file", Map.of("path", path)));
    }

    @Test void readsExistingFile() throws IOException {
        Files.writeString(sandbox.resolve("hello.txt"), "Hello MARK");
        ToolResult result = read("hello.txt");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("Hello MARK"));
    }

    @Test void fileNotFoundReturnsFailed() {
        ToolResult result = read("missing.txt");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("not found"));
    }

    @Test void pathTraversalReturnsFailed() {
        ToolResult result = read("../secret.txt");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("escapes sandbox"));
    }

    @Test void fileTooLargeReturnsFailed() throws IOException {
        byte[] large = new byte[101 * 1024];
        Files.write(sandbox.resolve("big.bin"), large);
        ToolResult result = read("big.bin");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("too large"));
    }

    @Test void directoryInsteadOfFileReturnsFailed() throws IOException {
        Files.createDirectory(sandbox.resolve("subdir"));
        ToolResult result = read("subdir");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Not a regular file"));
    }

    @Test void missingPathArgumentReturnsFailed() {
        ToolResult result = tool.execute(new ToolRequest(UUID.randomUUID(), "read_file", Map.of()));
        assertFalse(result.successful());
    }
}
