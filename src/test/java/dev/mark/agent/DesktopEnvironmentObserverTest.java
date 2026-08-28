package dev.mark.agent;

import dev.mark.tool.InspectUiTool;
import dev.mark.tool.ToolResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DesktopEnvironmentObserverTest {

    @Test
    void observesActiveWindow() {
        InspectUiTool inspectUiTool = mock(InspectUiTool.class);

        when(inspectUiTool.execute(any()))
                .thenReturn(ToolResult.success(
                        "Title: Google Chrome\nProcess: chrome\nPID: 1234"
                ));

        DesktopEnvironmentObserver observer =
                new DesktopEnvironmentObserver(inspectUiTool);

        WorldState state = observer.observe();

        assertEquals("Google Chrome", state.activeWindow());
        assertEquals("chrome", state.activeApplication());
    }
}