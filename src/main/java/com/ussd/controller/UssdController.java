package com.ussd.controller;

import com.ussd.engine.UssdEngine;
import com.ussd.model.UssdResponse;
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
                sessionId, phoneNumber, serviceCode, text);

        UssdResponse response = engine.process(sessionId, phoneNumber, serviceCode, text);

        log.info("USSD response — session: {}, continue: {}, message: '{}'",
                sessionId, response.isContinueSession(),
                response.getMessage().replace("\n", "\\n"));

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
                sessionId, phoneNumber, input);

        UssdResponse response = engine.processStep(sessionId, phoneNumber, serviceCode, input);

        return Map.of(
                "message", response.getMessage(),
                "continueSession", response.isContinueSession()
        );
    }
}
