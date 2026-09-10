package dev.mark.agent.service;


import dev.mark.tool.core.Tool;
import dev.mark.tool.dto.*;
import dev.mark.llm.dto.*;
import dev.mark.agent.model.AgentWorldStateDeltaModel;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * - What it does: Formats environment state changes into a human-readable string for AI agents.
 * - Why it is useful: Converts raw world state deltas into concise text summaries to provide essential context to LLMs.
 * - Application flow: Acts as a utility service invoked during agent execution cycles to process environmental updates.
 * - Methods and variables: Provides the formatWorldStateDelta method, which evaluates delta objects to build descriptive summary strings.
 * - Logic integration: Inspects AgentWorldStateDeltaModel for changes and conditionally appends differences regarding active applications, windows, and browser details.
 */

/**
 * - What it does: Formats environment state changes into a human-readable string for AI agents.
 * - Why it is useful: Converts raw world state deltas into concise text summaries to provide essential context to LLMs.
 * - Application flow: Acts as a utility service invoked during agent execution cycles to process environmental updates.
 * - Methods and variables: Features the formatWorldStateDelta method, which evaluates delta objects to build descriptive summary strings.
 * - Logic integration: Inspects AgentWorldStateDeltaModel for changes and conditionally appends differences regarding active applications, windows, and browser details.
 */

/**
 * - What it does: Formats environment state changes into a human-readable string for AI agents.
 * - Why it is useful: Converts raw world state deltas into concise text summaries to provide essential context to LLMs.
 * - Application flow: Acts as a utility service invoked during agent execution cycles to process environmental updates.
 * - Methods and variables: Provides the formatWorldStateDelta method, which evaluates delta objects to build descriptive summary strings.
 * - Logic integration: Inspects AgentWorldStateDeltaModel for changes and conditionally appends differences regarding active applications, windows, and browser details.
 */

/**
 * - What it does: Formats environment state changes into a human-readable string for AI agents.
 * - Why it is useful: Converts raw world state deltas into concise text summaries to provide context to LLMs.
 * - Application flow: Acts as a utility service invoked during agent execution cycles to process environmental updates.
 * - Methods and variables: Contains the formatWorldStateDelta method which evaluates delta changes and builds the summary string.
 * - Logic integration: Checks if AgentWorldStateDeltaModel has changes and appends differences in active apps, windows, and browser details.
 */

@Service
public class AgentEnvironmentDeltaService {

    public String formatWorldStateDelta(AgentWorldStateDeltaModel delta) {
        if (!delta.hasChanges()) {
            return "No environment changes detected.";
        }

        StringBuilder sb = new StringBuilder();

        if (!Objects.equals(
                delta.previousActiveApplication(),
                delta.currentActiveApplication())) {

            sb.append("- Active application changed from '")
                    .append(delta.previousActiveApplication())
                    .append("' to '")
                    .append(delta.currentActiveApplication())
                    .append("'\n");
        }

        if (!Objects.equals(
                delta.previousActiveWindow(),
                delta.currentActiveWindow())) {

            sb.append("- Active window changed from '")
                    .append(delta.previousActiveWindow())
                    .append("' to '")
                    .append(delta.currentActiveWindow())
                    .append("'\n");
        }

        if (!Objects.equals(
                delta.previousBrowserUrl(),
                delta.currentBrowserUrl())) {

            sb.append("- Browser URL changed from '")
                    .append(delta.previousBrowserUrl())
                    .append("' to '")
                    .append(delta.currentBrowserUrl())
                    .append("'\n");
        }

        if (!Objects.equals(
                delta.previousBrowserTitle(),
                delta.currentBrowserTitle())) {

            sb.append("- Browser title changed from '")
                    .append(delta.previousBrowserTitle())
                    .append("' to '")
                    .append(delta.currentBrowserTitle())
                    .append("'\n");
        }

        return sb.toString().trim();
    }
}
