package ru.dront78.pulsedroid.exception;

public class StoppedException extends RuntimeException {
    public StoppedException() {
    }

    public StoppedException(String message) {
        super(message);
    }

    public StoppedException(String message, Throwable cause) {
        super(message, cause);
    }

    public StoppedException(Throwable cause) {
        super(cause);
    }
}
