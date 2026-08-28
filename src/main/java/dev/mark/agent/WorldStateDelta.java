package dev.mark.agent;

public record WorldStateDelta(
        String previousActiveApplication,
        String currentActiveApplication,
        String previousActiveWindow,
        String currentActiveWindow,
        String previousBrowserUrl,
        String currentBrowserUrl,
        String previousBrowserTitle,
        String currentBrowserTitle) {

    public static WorldStateDelta between(
            WorldState previous,
            WorldState current) {

        return new WorldStateDelta(
                value(previous, true, true),
                value(current, true, true),
                value(previous, true, false),
                value(current, true, false),
                value(previous, false, true),
                value(current, false, true),
                value(previous, false, false),
                value(current, false, false)
        );
    }

    public boolean hasChanges() {
        return !equals(previousActiveApplication, currentActiveApplication)
                || !equals(previousActiveWindow, currentActiveWindow)
                || !equals(previousBrowserUrl, currentBrowserUrl)
                || !equals(previousBrowserTitle, currentBrowserTitle);
    }

    private static String value(
            WorldState state,
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