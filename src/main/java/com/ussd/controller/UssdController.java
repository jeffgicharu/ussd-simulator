package com.ussd.controller;

import com.ussd.engine.SessionLimitExceededException;
import com.ussd.engine.UssdEngine;
import com.ussd.model.UssdResponse;
import com.ussd.util.LogSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * USSD endpoint compatible with Africa's Talking gateway.
 * <p>
 * Africa's Talking sends a POST with form-encoded fields:
 *   sessionId, phoneNumber, serviceCode, text
 * and expects a plain text response prefixed with "CON " or "END ".
 */
@RestController
@RequestMapping("/ussd")
@RequiredArgsConstructor
@Slf4j
public class UssdController {

    private final UssdEngine engine;

    /** Shown when the concurrent-session cap is reached (see issue #7). */
    private static final UssdResponse SERVICE_BUSY = UssdResponse.end(
            "Service is temporarily unavailable. Please try again later.");

    /**
     * Africa's Talking compatible endpoint.
     * Accepts form-encoded POST and returns plain text.
     */
    @PostMapping(value = "/callback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_PLAIN_VALUE)
    public String handleCallback(
            @RequestParam String sessionId,
            @RequestParam String phoneNumber,
            @RequestParam(defaultValue = "*384#") String serviceCode,
            @RequestParam(defaultValue = "") String text) {

        log.info("USSD request — session: {}, phone: {}, code: {}, text: '{}'",
                LogSanitizer.clean(sessionId), LogSanitizer.clean(phoneNumber),
                LogSanitizer.clean(serviceCode), LogSanitizer.maskInput(text));

        UssdResponse response;
        try {
            response = engine.process(sessionId, phoneNumber, serviceCode, text);
        } catch (SessionLimitExceededException e) {
            log.warn("Session cap reached — returning graceful END for session {}",
                    LogSanitizer.clean(sessionId));
            response = SERVICE_BUSY;
        }

        log.info("USSD response — session: {}, continue: {}",
                LogSanitizer.clean(sessionId), response.isContinueSession());

        return response.toAfricasTalking();
    }

    /**
     * JSON API endpoint for the web simulator and other clients.
     * Accepts a single input per request (not the cumulative text chain).
     */
    @PostMapping(value = "/api",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handleJson(@RequestBody Map<String, String> request) {

        String sessionId = request.getOrDefault("sessionId", "");
        String phoneNumber = request.getOrDefault("phoneNumber", "");
        String serviceCode = request.getOrDefault("serviceCode", "*384#");
        String input = request.getOrDefault("input", "");

        log.info("USSD JSON — session: {}, phone: {}, input: '{}'",
                LogSanitizer.clean(sessionId), LogSanitizer.clean(phoneNumber),
                LogSanitizer.maskInput(input));

        UssdResponse response;
        try {
            response = engine.processStep(sessionId, phoneNumber, serviceCode, input);
        } catch (SessionLimitExceededException e) {
            log.warn("Session cap reached — returning graceful END for session {}",
                    LogSanitizer.clean(sessionId));
            response = SERVICE_BUSY;
        }

        return Map.of(
                "message", response.getMessage(),
                "continueSession", response.isContinueSession()
        );
    }
}
