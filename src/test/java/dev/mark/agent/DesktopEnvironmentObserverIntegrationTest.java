package dev.mark.agent;

import dev.mark.tool.InspectUiTool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DesktopEnvironmentObserverIntegrationTest {

    @Test
    void readsRealActiveWindow() {

        InspectUiTool inspectUiTool = new InspectUiTool();

        DesktopEnvironmentObserver observer =
                new DesktopEnvironmentObserver(inspectUiTool);

        WorldState state = observer.observe();

        assertNotNull(state.activeWindow());
        assertFalse(state.activeWindow().isBlank());

        assertNotNull(state.activeApplication());
        assertFalse(state.activeApplication().isBlank());
    }
}