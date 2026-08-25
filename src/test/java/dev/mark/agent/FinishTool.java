package dev.mark.agent;

import dev.mark.tool.Tool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import java.util.Map;

class FinishTool implements Tool {
    public String name() { return "finish"; }
    public String description() { return "Record that the deterministic task reached its end state."; }
    public ToolResult execute(ToolRequest request) { return ToolResult.success("deterministic end state reached"); }
}
