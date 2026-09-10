package dev.mark.agent.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentWorldStateDeltaModelTest {

    private AgentWorldStateModel createState(String app, String window, String url, String title) {
        AgentWorldStateModel state = new AgentWorldStateModel();
        state.setActiveApplication(app);
        state.setActiveWindow(window);
        state.setBrowserUrl(url);
        state.setBrowserTitle(title);
        return state;
    }

    @Test
    void shouldDetectNoChangesWhenStatesAreIdentical() {
        AgentWorldStateModel previous = createState("App1", "Window1", "http://example.com", "Title1");
        AgentWorldStateModel current = createState("App1", "Window1", "http://example.com", "Title1");

        AgentWorldStateDeltaModel delta = AgentWorldStateDeltaModel.between(previous, current);

        assertFalse(delta.hasChanges());
        assertEquals("App1", delta.previousActiveApplication());
        assertEquals("App1", delta.currentActiveApplication());
    }

    @Test
    void shouldDetectChangesWhenStatesDiffer() {
        AgentWorldStateModel previous = createState("App1", "Window1", "http://example.com", "Title1");
        AgentWorldStateModel current = createState("App2", "Window2", "http://example.org", "Title2");

        AgentWorldStateDeltaModel delta = AgentWorldStateDeltaModel.between(previous, current);

        assertTrue(delta.hasChanges());
        assertEquals("App1", delta.previousActiveApplication());
        assertEquals("App2", delta.currentActiveApplication());
        assertEquals("Window1", delta.previousActiveWindow());
        assertEquals("Window2", delta.currentActiveWindow());
        assertEquals("http://example.com", delta.previousBrowserUrl());
        assertEquals("http://example.org", delta.currentBrowserUrl());
        assertEquals("Title1", delta.previousBrowserTitle());
        assertEquals("Title2", delta.currentBrowserTitle());
    }

    @Test
    void shouldHandleNullStates() {
        AgentWorldStateModel previous = null;
        AgentWorldStateModel current = createState("App1", "Window1", "http://example.com", "Title1");

        AgentWorldStateDeltaModel delta = AgentWorldStateDeltaModel.between(previous, current);

        assertTrue(delta.hasChanges());
        assertNull(delta.previousActiveApplication());
        assertEquals("App1", delta.currentActiveApplication());
    }
}
