package dev.mark.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiLlmProviderTest {

    @Test
    void testProviderName() {
        GeminiLlmProperties props = new GeminiLlmProperties("test-key", "gemini-2.5-flash");
        GeminiLlmProvider provider = new GeminiLlmProvider(props);
        assertEquals("gemini", provider.name());
    }
}
