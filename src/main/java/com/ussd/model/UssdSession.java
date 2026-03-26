package com.ussd.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class UssdSession {

    private final String sessionId;
    private final String phoneNumber;
    private final String serviceCode;
    private final Instant createdAt;

    private String currentScreenId;
    private Deque<String> screenHistory;
    private Map<String, String> data;
    private Instant lastAccessedAt;
    private boolean active;

    public UssdSession(String sessionId, String phoneNumber, String serviceCode) {
        this.sessionId = sessionId;
        this.phoneNumber = phoneNumber;
        this.serviceCode = serviceCode;
        this.createdAt = Instant.now();
        this.lastAccessedAt = Instant.now();
        this.currentScreenId = "MAIN_MENU";
        this.screenHistory = new ArrayDeque<>();
        this.data = new HashMap<>();
        this.active = true;
    }

    public void navigateTo(String screenId) {
        screenHistory.push(currentScreenId);
        currentScreenId = screenId;
        touch();
    }

    public String goBack() {
        if (!screenHistory.isEmpty()) {
            currentScreenId = screenHistory.pop();
        }
        touch();
        return currentScreenId;
    }

    public void putData(String key, String value) {
        data.put(key, value);
    }

    public String getData(String key) {
        return data.get(key);
    }

    public void touch() {
        lastAccessedAt = Instant.now();
    }

    public void end() {
        active = false;
    }
}
