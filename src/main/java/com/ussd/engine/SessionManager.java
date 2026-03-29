package com.ussd.engine;

import com.ussd.entity.SessionLog;
import com.ussd.model.UssdSession;
import com.ussd.repository.SessionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SessionManager {

    @Autowired(required = false)
    private SessionLogRepository sessionLogRepo;

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
            persistSessionLog(session, "COMPLETED");
            log.info("Session ended: {}", sessionId);
        }
    }

    private void persistSessionLog(UssdSession session, String outcome) {
        if (sessionLogRepo == null) return;
        try {
            long duration = Duration.between(session.getCreatedAt(), Instant.now()).toMillis();
            String path = session.getScreenHistory().stream().collect(Collectors.joining(" > "));

            sessionLogRepo.save(SessionLog.builder()
                    .sessionId(session.getSessionId())
                    .phoneNumber(session.getPhoneNumber())
                    .serviceCode(session.getServiceCode())
                    .screenCount(session.getScreenHistory().size() + 1)
                    .screenPath(path.length() > 500 ? path.substring(0, 500) : path)
                    .lastScreen(session.getCurrentScreenId())
                    .outcome(outcome)
                    .durationMs(duration)
                    .endedAt(LocalDateTime.now())
                    .build());
        } catch (Exception e) {
            log.warn("Failed to persist session log: {}", e.getMessage());
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
