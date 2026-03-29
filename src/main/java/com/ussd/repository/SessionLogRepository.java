package com.ussd.repository;

import com.ussd.entity.SessionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface SessionLogRepository extends JpaRepository<SessionLog, Long> {
    List<SessionLog> findByPhoneNumberOrderByStartedAtDesc(String phoneNumber);

    long countByStartedAtAfter(LocalDateTime since);

    @Query("SELECT AVG(s.durationMs) FROM SessionLog s WHERE s.endedAt IS NOT NULL")
    Double averageDurationMs();

    @Query("SELECT s.lastScreen, COUNT(s) FROM SessionLog s WHERE s.lastScreen IS NOT NULL GROUP BY s.lastScreen ORDER BY COUNT(s) DESC")
    List<Object[]> dropOffByScreen();

    @Query("SELECT s.outcome, COUNT(s) FROM SessionLog s GROUP BY s.outcome")
    List<Object[]> countByOutcome();
}
