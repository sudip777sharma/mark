package dev.mark.agent;

import dev.mark.tool.InspectUiTool;
import dev.mark.tool.ToolRequest;
import dev.mark.tool.ToolResult;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class DesktopEnvironmentObserver implements EnvironmentObserver {

    private final InspectUiTool inspectUiTool;

    public DesktopEnvironmentObserver(InspectUiTool inspectUiTool) {
        this.inspectUiTool = inspectUiTool;
    }

    @Override
    public WorldState observe() {
        WorldState worldState = new WorldState();

        ToolRequest request = new ToolRequest(
                UUID.randomUUID(),
                "environment_observation",
                Map.of("action", "get_active_window")
        );

        ToolResult result = inspectUiTool.execute(request);

        if (!result.successful()) {
            return worldState;
        }

        String observation = result.observation();

        String activeWindow = extractValue(observation, "Title:");
        String activeApplication = extractValue(observation, "Process:");

        worldState.setActiveWindow(activeWindow);
        worldState.setActiveApplication(activeApplication);

        return worldState;
    }

    private String extractValue(String observation, String prefix) {
        if (observation == null || observation.isBlank()) {
            return null;
        }

        for (String line : observation.split("\\R")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }

        return null;
    }
}