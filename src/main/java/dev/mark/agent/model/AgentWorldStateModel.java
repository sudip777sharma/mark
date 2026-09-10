package dev.mark.agent.model;

/**
 * Represents the current state of the user's desktop environment by tracking
 * active applications, windows, and browser details.
 *
 * - **What it does:** Captures the OS and browser environment into a structured
 * data model.
 * - **Why it is useful:** Provides a reliable and standardized container to
 * inspect and transfer user context snapshots.
 * - **Application flow:** Functions as a telemetry data object populated by
 * system monitors and consumed by agents.
 * - **Methods and variables:** Fields store raw state details, empty() provides
 * a blank instance, and compareTo() calculates model differences.
 * - **Logic integration:** Supplies the baseline context within the agent
 * execution loop to compute state deltas and drive automation.
 */

public final class AgentWorldStateModel {

    private String activeApplication;
    private String activeWindow;
    private String browserUrl;
    private String browserTitle;

    public static AgentWorldStateModel empty() {
        return new AgentWorldStateModel();
    }

    public AgentWorldStateDeltaModel compareTo(AgentWorldStateModel other) {
        return AgentWorldStateDeltaModel.between(other, this);
    }

    public String activeApplication() {
        return activeApplication;
    }

    public void setActiveApplication(String activeApplication) {
        this.activeApplication = activeApplication;
    }

    public String activeWindow() {
        return activeWindow;
    }

    public void setActiveWindow(String activeWindow) {
        this.activeWindow = activeWindow;
    }

    public String browserUrl() {
        return browserUrl;
    }

    public void setBrowserUrl(String browserUrl) {
        this.browserUrl = browserUrl;
    }

    public String browserTitle() {
        return browserTitle;
    }

    public void setBrowserTitle(String browserTitle) {
        this.browserTitle = browserTitle;
    }
}
