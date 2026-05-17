package com.ussd.engine;

/**
 * Thrown by {@link SessionManager} when {@code ussd.max-sessions} is
 * reached and a new session cannot be allocated. Carries no extra state;
 * the controller boundary turns it into a graceful USSD {@code END}
 * response rather than letting it surface as an unhandled servlet error.
 */
public class SessionLimitExceededException extends RuntimeException {
    public SessionLimitExceededException(String message) {
        super(message);
    }
}
