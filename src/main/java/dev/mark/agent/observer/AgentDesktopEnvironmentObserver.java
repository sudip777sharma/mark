package dev.mark.agent.observer;

import dev.mark.agent.model.AgentWorldStateModel;
import dev.mark.tool.impl.desktop.InspectUiTool;
import dev.mark.tool.dto.ToolRequestDTO;
import dev.mark.tool.dto.ToolResultDTO;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * - What the class does: Observes the desktop environment by capturing active
 * window and application details.
 * - Why it is useful: Translates raw operating system GUI data into a
 * structured world state for the AI agent.
 * - How it fits in the flow of the application: Acts as a sensor in the agent's
 * perception-action loop, executed periodically to refresh world context.
 * - Its methods and variables and how they are useful: Uses InspectUiTool to
 * fetch desktop data, observe() to build the AgentWorldStateModel, and
 * extractValue() to parse specific metadata.
 * - Its logic and how it gets fit into the overall application logic: Requests
 * active window data, parses the tool output string safely, populates state
 * models, and integrates cleanly via the AgentEnvironmentObserver interface.
 */

@Component
public class AgentDesktopEnvironmentObserver implements AgentEnvironmentObserver {

    private final InspectUiTool inspectUiTool;

    public AgentDesktopEnvironmentObserver(InspectUiTool inspectUiTool) {
        this.inspectUiTool = inspectUiTool;
    }

    @Override
    public AgentWorldStateModel observe() {
        AgentWorldStateModel worldState = new AgentWorldStateModel();

        ToolRequestDTO request = new ToolRequestDTO(
                UUID.randomUUID(),
                "environment_observation",
                Map.of("action", "get_active_window"));

        ToolResultDTO result = inspectUiTool.execute(request);

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
