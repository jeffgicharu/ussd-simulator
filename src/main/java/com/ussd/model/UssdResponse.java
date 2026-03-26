package com.ussd.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UssdResponse {

    private final String message;
    private final boolean continueSession;

    public static UssdResponse con(String message) {
        return new UssdResponse(message, true);
    }

    public static UssdResponse end(String message) {
        return new UssdResponse(message, false);
    }

    /**
     * Format for Africa's Talking API:
     * "CON message" to continue, "END message" to terminate
     */
    public String toAfricasTalking() {
        return (continueSession ? "CON " : "END ") + message;
    }
}
