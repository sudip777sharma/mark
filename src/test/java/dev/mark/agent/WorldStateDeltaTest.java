package dev.mark.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldStateDeltaTest {

    @Test
    void detectsChangedApplicationAndWindow() {
        WorldState previous = new WorldState();
        previous.setActiveApplication("chrome");
        previous.setActiveWindow("Google");

        WorldState current = new WorldState();
        current.setActiveApplication("notepad");
        current.setActiveWindow("Untitled - Notepad");

        WorldStateDelta delta =
                WorldStateDelta.between(previous, current);

        assertTrue(delta.hasChanges());
        assertEquals("chrome", delta.previousActiveApplication());
        assertEquals("notepad", delta.currentActiveApplication());
        assertEquals("Google", delta.previousActiveWindow());
        assertEquals("Untitled - Notepad", delta.currentActiveWindow());
    }

    @Test
    void detectsNoChanges() {
        WorldState previous = new WorldState();
        previous.setActiveApplication("chrome");
        previous.setActiveWindow("Google");

        WorldState current = new WorldState();
        current.setActiveApplication("chrome");
        current.setActiveWindow("Google");

        WorldStateDelta delta =
                WorldStateDelta.between(previous, current);

        assertFalse(delta.hasChanges());
    }
}