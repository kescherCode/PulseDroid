package ru.dront78.pulsedroid;

public enum PlayState {
    STOPPED(false),
    STARTING(true),
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
