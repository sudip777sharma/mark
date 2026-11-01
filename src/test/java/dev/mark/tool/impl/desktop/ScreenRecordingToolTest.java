package dev.mark.tool.impl.desktop;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScreenRecordingToolTest {

    private ScreenRecordingTool tool;
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ScreenRecordingTool();
    }

    @Test
    void shouldReturnCorrectMetadata() {
        assertEquals("screen_recorder", tool.name());
        assertTrue(tool.description().contains("Record the Windows desktop screen"));

        Map<String, Object> schema = tool.parameterSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertTrue(props.containsKey("action"));
        assertTrue(props.containsKey("duration_seconds"));
        assertTrue(props.containsKey("fps"));
        assertTrue(props.containsKey("include_audio"));
        assertTrue(props.containsKey("audio_device"));
        assertTrue(props.containsKey("output_path"));
    }

    @Test
    void executeShouldFailWhenActionIsMissing() {
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of());
        ToolResultDTO result = tool.execute(request);

        assertFalse(result.successful());
        assertTrue(result.observation().contains("requires an 'action' argument"));
    }

    @Test
    void executeShouldFailWhenActionIsBlank() {
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "   "));
        ToolResultDTO result = tool.execute(request);

        assertFalse(result.successful());
        assertTrue(result.observation().contains("requires an 'action' argument"));
    }

    @Test
    void executeShouldFailWhenActionIsUnknown() {
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "fly_to_moon"));
        ToolResultDTO result = tool.execute(request);

        assertFalse(result.successful());
        assertTrue(result.observation().contains("Unknown screen_recorder action"));
    }

    @Test
    void handleStatusShouldReportIdleWhenNotRecording() {
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "status"));
        ToolResultDTO result = tool.execute(request);

        assertTrue(result.successful());
        assertEquals("IDLE", result.metadata().get("status"));
        assertEquals(Boolean.FALSE, result.metadata().get("recording"));
    }

    @Test
    void handleStopRecordingShouldReturnIdleWhenNoRecordingActive() {
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "stop_recording"));
        ToolResultDTO result = tool.execute(request);

        assertFalse(result.successful());
        assertEquals("IDLE", result.metadata().get("status"));
        assertTrue(result.observation().contains("No screen recording is currently active"));
    }
}
