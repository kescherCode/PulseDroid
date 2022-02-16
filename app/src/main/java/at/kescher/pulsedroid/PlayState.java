package at.kescher.pulsedroid;

public enum PlayState {
    STOPPED(false),
    STARTING(true),
    BUFFERING(true),
    STARTED(true),
    STOPPING(false);

    private final boolean isActive;

    PlayState(boolean active) {
        isActive = active;
    }

    public boolean isActive() {
        return isActive;
    }
}
