package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EchoToolTest {
    private final EchoTool echoTool = new EchoTool();

    @Test void acceptsAStringMessageFromStructuredArguments() {
        ToolResult result = echoTool.execute(new ToolRequest(UUID.randomUUID(), "echo", Map.of("message", "hello MARK")));
        assertTrue(result.successful());
    }

    @Test void rejectsNonStringMessageWithoutCasting() {
        ToolResult result = echoTool.execute(new ToolRequest(UUID.randomUUID(), "echo", Map.of("message", 42)));
        assertFalse(result.successful());
    }
}
