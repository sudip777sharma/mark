package dev.mark.agent.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentPropertiesConfigTest {

    @Test
    void shouldSetDefaultsWhenNullPropertiesProvided() {
        AgentPropertiesConfig config = new AgentPropertiesConfig(30, null, null);

        assertEquals(30, config.maxSteps());
        assertEquals(6, config.maxHistoryLength(), "Default history length should be 6");
        assertEquals(20000, config.maxHistoryChars(), "Default history chars should be 20000");
    }

    @Test
    void shouldKeepProvidedValuesWhenNotNull() {
        AgentPropertiesConfig config = new AgentPropertiesConfig(50, 10, 50000);

        assertEquals(50, config.maxSteps());
        assertEquals(10, config.maxHistoryLength());
        assertEquals(50000, config.maxHistoryChars());
    }
}
