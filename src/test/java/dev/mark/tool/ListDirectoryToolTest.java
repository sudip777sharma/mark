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

class ListDirectoryToolTest {
    @TempDir Path sandbox;
    private ListDirectoryTool tool;

    @BeforeEach void setUp() {
        tool = new ListDirectoryTool(new FileSystemProperties(sandbox.toString()));
    }

    private ToolResult list(String path) {
        return tool.execute(new ToolRequest(UUID.randomUUID(), "list_directory", Map.of("path", path)));
    }

    @Test void listsFilesAndDirectories() throws IOException {
        Files.writeString(sandbox.resolve("readme.txt"), "hello");
        Files.createDirectory(sandbox.resolve("data"));
        ToolResult result = list(".");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("readme.txt"));
        assertTrue(result.observation().contains("data/"));
    }

    @Test void listsEmptyDirectory() throws IOException {
        Files.createDirectory(sandbox.resolve("empty"));
        ToolResult result = list("empty");
        assertTrue(result.successful());
        assertTrue(result.observation().contains("empty"));
    }

    @Test void defaultsToRootWhenNoPathProvided() throws IOException {
        Files.writeString(sandbox.resolve("file.txt"), "content");
        ToolResult result = tool.execute(new ToolRequest(UUID.randomUUID(), "list_directory", Map.of()));
        assertTrue(result.successful());
        assertTrue(result.observation().contains("file.txt"));
    }

    @Test void pathTraversalReturnsFailed() {
        ToolResult result = list("../../");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("escapes sandbox"));
    }

    @Test void fileInsteadOfDirectoryReturnsFailed() throws IOException {
        Files.writeString(sandbox.resolve("afile.txt"), "not a dir");
        ToolResult result = list("afile.txt");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Not a directory"));
    }

    @Test void directoryNotFoundReturnsFailed() {
        ToolResult result = list("nonexistent");
        assertFalse(result.successful());
        assertTrue(result.observation().contains("not found"));
    }
}
