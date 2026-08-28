package dev.mark.agent;

public final class WorldState {

    private String activeApplication;
    private String activeWindow;
    private String browserUrl;
    private String browserTitle;

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