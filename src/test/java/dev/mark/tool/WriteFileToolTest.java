package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class WriteFileToolTest {
    @TempDir Path sandbox;
    private WriteFileTool tool;

    @BeforeEach void setUp() {
        tool = new WriteFileTool(new FileSystemProperties(sandbox.toString()));
    }

    private ToolResult write(String path, String content) {
        return tool.execute(new ToolRequest(UUID.randomUUID(), "write_file",
                Map.of("path", path, "content", content)));
    }

    @Test void writesNewFile() throws IOException {
        ToolResult result = write("output.txt", "Hello from MARK");
        assertTrue(result.successful());
        assertEquals("Hello from MARK", Files.readString(sandbox.resolve("output.txt")));
    }

    @Test void overwritesExistingFile() throws IOException {
        Files.writeString(sandbox.resolve("existing.txt"), "old content");
        ToolResult result = write("existing.txt", "new content");
        assertTrue(result.successful());
        assertEquals("new content", Files.readString(sandbox.resolve("existing.txt")));
    }

    @Test void createsParentDirectories() throws IOException {
        ToolResult result = write("sub/dir/file.txt", "nested");
        assertTrue(result.successful());
        assertEquals("nested", Files.readString(sandbox.resolve("sub/dir/file.txt")));
    }

    @Test void pathTraversalReturnsFailed() {
        ToolResult result = write("../../escape.txt", "bad");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("escapes sandbox"));
    }

    @Test void missingContentReturnsFailed() {
        ToolResult result = tool.execute(new ToolRequest(UUID.randomUUID(), "write_file",
                Map.of("path", "file.txt")));
        assertFalse(result.successful());
    }

    @Test void missingPathReturnsFailed() {
        ToolResult result = tool.execute(new ToolRequest(UUID.randomUUID(), "write_file",
                Map.of("content", "hello")));
        assertFalse(result.successful());
    }
}
