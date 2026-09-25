package dev.mark.agent.observer;

import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import dev.mark.tool.impl.desktop.InspectUiTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentDesktopEnvironmentObserverTest {

    private InspectUiTool inspectUiTool;
    private AgentDesktopEnvironmentObserver observer;

    @BeforeEach
    void setUp() {
        inspectUiTool = mock(InspectUiTool.class);
        observer = new AgentDesktopEnvironmentObserver(inspectUiTool);
    }

    @Test
    void shouldReturnEmptyStateWhenToolExecutionFails() {
        // Arrange
        ToolResultDTO failedResult = new ToolResultDTO(false, "Error", Map.of());
        when(inspectUiTool.execute(any(ToolRequestDTO.class))).thenReturn(failedResult);

        // Act
        AgentWorldStateModel state = observer.observe();

        // Assert
        assertNull(state.activeWindow());
        assertNull(state.activeApplication());

        verify(inspectUiTool, org.mockito.Mockito.atLeastOnce()).execute(any(ToolRequestDTO.class));
    }

    @Test
    void shouldExtractWindowAndApplicationWhenToolExecutionSucceeds() {
        String mockObservation = "Process: chrome.exe\nTitle: GitHub - Mozilla Firefox\nOtherData: ignored";
        when(inspectUiTool.execute(any(ToolRequestDTO.class)))
                .thenReturn(ToolResultDTO.success(mockObservation));

        AgentWorldStateModel state = observer.observe();

        assertEquals("GitHub - Mozilla Firefox", state.activeWindow());
        assertEquals("chrome.exe", state.activeApplication());

        ArgumentCaptor<ToolRequestDTO> captor = ArgumentCaptor.forClass(ToolRequestDTO.class);
        verify(inspectUiTool, org.mockito.Mockito.atLeastOnce()).execute(captor.capture());
        ToolRequestDTO capturedRequest = captor.getValue();
        assertEquals("environment_observation", capturedRequest.action());
        assertEquals("get_active_window", capturedRequest.arguments().get("action"));
    }

    @Test
    void shouldHandleMissingFieldsInObservation() {
        // Arrange
        String observationOutput = "Title: Only Title Here\r\nRandom: Data";
        ToolResultDTO successResult = new ToolResultDTO(true, observationOutput, Map.of());
        when(inspectUiTool.execute(any(ToolRequestDTO.class))).thenReturn(successResult);

        // Act
        AgentWorldStateModel state = observer.observe();

        // Assert
        assertEquals("Only Title Here", state.activeWindow());
        assertNull(state.activeApplication());
    }

    @Test
    void shouldHandleNullOrBlankObservation() {
        // Arrange
        ToolResultDTO emptyResult = new ToolResultDTO(true, "   ", Map.of());
        when(inspectUiTool.execute(any(ToolRequestDTO.class))).thenReturn(emptyResult);

        // Act
        AgentWorldStateModel state = observer.observe();

        // Assert
        assertNull(state.activeWindow());
        assertNull(state.activeApplication());
    }
}
