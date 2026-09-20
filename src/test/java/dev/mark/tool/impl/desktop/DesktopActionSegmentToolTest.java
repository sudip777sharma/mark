package dev.mark.tool.impl.desktop;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mark.agent.model.action.Action;
import dev.mark.agent.model.ui.UiElementModel;
import dev.mark.agent.service.TransitionAnalyzerService;
import dev.mark.agent.service.ui.ElementResolver;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DesktopActionSegmentToolTest {

    private ElementResolver elementResolver;
    private DesktopActionExecutorService executorService;
    private TransitionAnalyzerService transitionAnalyzerService;
    private DesktopActionSegmentTool tool;

    @BeforeEach
    void setUp() {
        elementResolver = mock(ElementResolver.class);
        executorService = mock(DesktopActionExecutorService.class);
        transitionAnalyzerService = mock(TransitionAnalyzerService.class);
        
        when(transitionAnalyzerService.isTargetStillValid(anyString(), anyString())).thenReturn(true);
        
        ObjectMapper objectMapper = new ObjectMapper();
        tool = new DesktopActionSegmentTool(elementResolver, executorService, objectMapper, transitionAnalyzerService);
    }

    @Test
    void testSuccessfulSegment() {
        // Arrange
        String taskId = "00000000-0000-0000-0000-000000000001";
        UiElementModel mockTarget = mock(UiElementModel.class);
        when(mockTarget.id()).thenReturn("ui-1");
        
        when(elementResolver.resolve(taskId, "ui-1")).thenReturn(java.util.Optional.of(mockTarget));
        when(executorService.executeWait(100)).thenReturn(ToolResultDTO.success("Wait ok"));
        when(executorService.executeClick(mockTarget, "left")).thenReturn(ToolResultDTO.success("Click ok"));

        List<Map<String, Object>> segment = List.of(
            Map.of("type", "wait", "durationMs", 100),
            Map.of("type", "click", "targetId", "ui-1", "button", "left")
        );
        
        Map<String, Object> args = Map.of("actions", segment);

        dev.mark.tool.dto.ToolRequestDTO req = new dev.mark.tool.dto.ToolRequestDTO(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), tool.name(), args);

        // Act
        ToolResultDTO result = tool.execute(req);

        // Assert
        System.out.println("Result: " + result.observation());
        assertTrue(result.successful(), "Segment should succeed");
        assertTrue(result.observation().contains("Step 1: [wait] Wait ok"));
        assertTrue(result.observation().contains("Step 2: [click] Click ok"));
        assertTrue(result.observation().contains("COMPLETED SUCCESSFULLY"));
        
        verify(executorService).executeWait(100);
        verify(executorService).executeClick(mockTarget, "left");
    }

    @Test
    void testSegmentHaltsOnFailure() {
        // Arrange
        String taskId = "00000000-0000-0000-0000-000000000001";
        UiElementModel mockTarget = mock(UiElementModel.class);
        
        when(elementResolver.resolve(taskId, "ui-1")).thenReturn(java.util.Optional.of(mockTarget));
        when(executorService.executeClick(mockTarget, "left")).thenReturn(new ToolResultDTO(false, "Click failed", Map.of()));
        
        // Should halt after click, type should never happen
        List<Map<String, Object>> segment = List.of(
            Map.of("type", "click", "targetId", "ui-1", "button", "left"),
            Map.of("type", "type", "targetId", "ui-1", "text", "text")
        );
        
        Map<String, Object> args = Map.of("actions", segment);

        dev.mark.tool.dto.ToolRequestDTO req = new dev.mark.tool.dto.ToolRequestDTO(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), tool.name(), args);

        // Act
        ToolResultDTO result = tool.execute(req);

        // Assert
        System.out.println("Result: " + result.observation());
        assertFalse(result.successful(), "Segment should fail");
        assertTrue(result.observation().contains("Step 1: [click] Click failed"));
        assertTrue(result.observation().contains("HALTING SEGMENT"));
        
        verify(executorService).executeClick(mockTarget, "left");
        verify(executorService, never()).executeType(any(), any());
    }

    @Test
    void testSegmentHaltsOnTransition() {
        // Arrange
        String taskId = "00000000-0000-0000-0000-000000000001";
        UiElementModel mockTarget = mock(UiElementModel.class);
        
        when(elementResolver.resolve(taskId, "ui-1")).thenReturn(java.util.Optional.of(mockTarget));
        when(executorService.executeWait(100)).thenReturn(ToolResultDTO.success("Wait ok"));
        when(transitionAnalyzerService.isTargetStillValid(anyString(), eq("ui-1"))).thenReturn(false);
        
        List<Map<String, Object>> segment = List.of(
            Map.of("type", "wait", "durationMs", 100),
            Map.of("type", "click", "targetId", "ui-1", "button", "left")
        );
        
        Map<String, Object> args = Map.of("actions", segment);

        dev.mark.tool.dto.ToolRequestDTO req = new dev.mark.tool.dto.ToolRequestDTO(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), tool.name(), args);

        // Act
        ToolResultDTO result = tool.execute(req);

        // Assert
        assertFalse(result.successful(), "Segment should fail due to transition");
        assertTrue(result.observation().contains("Transition detected"));
        assertEquals(true, result.metadata().get("transition_detected"));
        
        verify(executorService).executeWait(100);
        verify(executorService, never()).executeClick(any(), anyString());
    }
}
