package dev.mark.agent.model;

/**
 * Represents the state changes between two distinct points in time for an agent
 * environment.
 *
 * - **What it does:** Captures the delta of desktop applications, active
 * windows, browser URLs, and browser titles.
 * - **Why it is useful:** Efficiently isolates context shifts and user activity
 * transitions without processing entire states.
 * - **Application flow:** Sits directly between sequential world state captures
 * to evaluate user transitions.
 * - **Methods and variables:** Holds previous and current environment fields;
 * uses the between() factory method to compute deltas, hasChanges() to check
 * for mutations, and helper methods for safe property extraction.
 * - **Logic integration:** Powers event-driven triggers by filtering out static
 * states and propagating only meaningful context changes to downstream agent
 * logic.
 */

public record AgentWorldStateDeltaModel(
        String previousActiveApplication,
        String currentActiveApplication,
        String previousActiveWindow,
        String currentActiveWindow,
        String previousBrowserUrl,
        String currentBrowserUrl,
        String previousBrowserTitle,
        String currentBrowserTitle) {

    public static AgentWorldStateDeltaModel between(
            AgentWorldStateModel previous,
            AgentWorldStateModel current) {

        return new AgentWorldStateDeltaModel(
                value(previous, true, true),
                value(current, true, true),
                value(previous, true, false),
                value(current, true, false),
                value(previous, false, true),
                value(current, false, true),
                value(previous, false, false),
                value(current, false, false));
    }

    public boolean hasChanges() {
        return !equals(previousActiveApplication, currentActiveApplication)
                || !equals(previousActiveWindow, currentActiveWindow)
                || !equals(previousBrowserUrl, currentBrowserUrl)
                || !equals(previousBrowserTitle, currentBrowserTitle);
    }

    private static String value(
            AgentWorldStateModel state,
            boolean desktop,
            boolean first) {

        if (state == null) {
            return null;
        }

        if (desktop && first) {
            return state.activeApplication();
        }

        if (desktop) {
            return state.activeWindow();
        }

        if (first) {
            return state.browserUrl();
        }

        return state.browserTitle();
    }

    private static boolean equals(String a, String b) {
        return java.util.Objects.equals(a, b);
    }
}
