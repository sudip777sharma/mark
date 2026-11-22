package dev.mark.tool.impl.desktop;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.vision.service.ScreenshotCacheService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScreenshotToolTest {

    @Test
    void testToolContractIdentity() {
        ScreenshotTool tool = new ScreenshotTool();
        assertEquals("screenshot", tool.name());
        assertNotNull(tool.contract());
        assertEquals("DESKTOP", tool.contract().capabilityCategory());
    }

    @Test
    void testExecuteCapture(@TempDir Path tempDir) {
        ScreenshotCacheService cache = new ScreenshotCacheService();
        ScreenshotTool tool = new ScreenshotTool(cache);

        Path testOut = tempDir.resolve("test_screenshot.png");
        ToolRequestDTO req = new ToolRequestDTO(UUID.randomUUID(), "screenshot", Map.of(
                "file_path", testOut.toString()
        ));

        ToolResultDTO result = tool.execute(req);
        assertTrue(result.successful(), "Screenshot capture should succeed: " + result.observation());

        File f = testOut.toFile();
        assertTrue(f.exists(), "Output file must exist");
        assertTrue(f.length() > 500, "Output file must be non-empty PNG: " + f.length());

        assertNotNull(cache.getLatest(), "Screenshot cache must be populated");
        assertEquals(f.getAbsolutePath(), cache.getLatest().filePath());
        assertTrue(cache.getLatest().dimension().width() > 0);
        assertTrue(cache.getLatest().dimension().height() > 0);
    }
}
