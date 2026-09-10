package dev.mark.tool.impl.desktop;

import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InspectUiToolTest {

    private InspectUiTool tool;
    private final UUID taskId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new InspectUiTool();
    }

    @Test
    void shouldReturnCorrectMetadata() {
        assertEquals("inspect_ui", tool.name());
        assertTrue(tool.description().contains("Read the UI element tree"));

        Map<String, Object> schema = tool.parameterSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertTrue(schema.containsKey("required"));
    }

    @Test
    void executeShouldFailWhenActionIsMissing() {
        // Arrange
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of());

        // Act
        ToolResultDTO result = tool.execute(request);

        // Assert
        assertFalse(result.successful());
        assertTrue(result.observation().contains("requires an 'action' argument"));
    }

    @Test
    void executeShouldFailWhenActionIsBlank() {
        // Arrange
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "   "));

        // Act
        ToolResultDTO result = tool.execute(request);

        // Assert
        assertFalse(result.successful());
        assertTrue(result.observation().contains("requires an 'action' argument"));
    }

    @Test
    void executeShouldFailForUnknownAction() {
        // Arrange
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "unknown_action"));

        // Act
        ToolResultDTO result = tool.execute(request);

        // Assert
        assertFalse(result.successful());
        assertTrue(result.observation().contains("Unknown inspect_ui action"));
    }

    @Test
    void executeShouldFailForInspectWindowWhenTitleIsMissing() {
        // Arrange
        ToolRequestDTO request = new ToolRequestDTO(taskId, tool.name(), Map.of("action", "inspect_window"));

        // Act
        ToolResultDTO result = tool.execute(request);

        // Assert
        assertFalse(result.successful());
        assertTrue(result.observation().contains("'title' is required for inspect_window"));
    }
}
