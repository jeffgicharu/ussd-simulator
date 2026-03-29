package com.ussd.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "session_logs", indexes = {
    @Index(name = "idx_session_phone", columnList = "phoneNumber"),
    @Index(name = "idx_session_started", columnList = "startedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SessionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String sessionId;

    @Column(nullable = false, length = 15)
    private String phoneNumber;

    private String serviceCode;

    private int screenCount;

    @Column(length = 500)
    private String screenPath;

    private String lastScreen;

    @Column(length = 20)
    private String outcome;

    private long durationMs;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @PrePersist
    void onCreate() { startedAt = LocalDateTime.now(); }
}
