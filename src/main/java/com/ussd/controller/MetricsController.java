package com.ussd.controller;

import com.ussd.engine.SessionManager;
import com.ussd.engine.UssdEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final SessionManager sessionManager;
    private final UssdEngine ussdEngine;

    @GetMapping
    public Map<String, Object> getMetrics() {
        return Map.of(
                "activeSessions", sessionManager.getActiveSessionCount(),
                "registeredScreens", ussdEngine.getScreenCount(),
                "timestamp", Instant.now().toString()
        );
    }
}
