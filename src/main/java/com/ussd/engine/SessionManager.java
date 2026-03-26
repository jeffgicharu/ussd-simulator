package com.ussd.engine;

import com.ussd.model.UssdSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SessionManager {

    private final Map<String, UssdSession> sessions = new ConcurrentHashMap<>();

    @Value("${ussd.session-timeout-seconds:180}")
    private int sessionTimeoutSeconds;

    @Value("${ussd.max-sessions:10000}")
    private int maxSessions;

    public UssdSession createSession(String sessionId, String phoneNumber, String serviceCode) {
        if (sessions.size() >= maxSessions) {
            cleanExpiredSessions();
            if (sessions.size() >= maxSessions) {
                throw new IllegalStateException("Maximum concurrent sessions reached");
            }
        }

        UssdSession session = new UssdSession(sessionId, phoneNumber, serviceCode);
        sessions.put(sessionId, session);
        log.info("Session created: {} for {}", sessionId, phoneNumber);
        return session;
    }

    public UssdSession getSession(String sessionId) {
        UssdSession session = sessions.get(sessionId);

        if (session == null) {
            return null;
        }

        if (!session.isActive() || isExpired(session)) {
            sessions.remove(sessionId);
            log.info("Session expired: {}", sessionId);
            return null;
        }

        session.touch();
        return session;
    }

    public void endSession(String sessionId) {
        UssdSession session = sessions.remove(sessionId);
        if (session != null) {
            session.end();
            log.info("Session ended: {}", sessionId);
        }
    }

    public int getActiveSessionCount() {
        return sessions.size();
    }

    private boolean isExpired(UssdSession session) {
        return Duration.between(session.getLastAccessedAt(), Instant.now())
                .getSeconds() > sessionTimeoutSeconds;
    }

    @Scheduled(fixedRate = 30000) // Every 30 seconds
    public void cleanExpiredSessions() {
        int before = sessions.size();
        sessions.entrySet().removeIf(entry ->
                !entry.getValue().isActive() || isExpired(entry.getValue()));
        int removed = before - sessions.size();
        if (removed > 0) {
            log.info("Cleaned {} expired sessions. Active: {}", removed, sessions.size());
        }
    }
}
