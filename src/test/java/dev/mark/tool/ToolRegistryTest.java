package dev.mark.tool;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolRegistryTest {
    @Test void resolvesRegisteredTool() { assertEquals("echo", new ToolRegistry(List.of(new EchoTool())).require("echo").name()); }
    @Test void rejectsUnknownTool() { assertThrows(IllegalArgumentException.class, () -> new ToolRegistry(List.of()).require("missing")); }
}
