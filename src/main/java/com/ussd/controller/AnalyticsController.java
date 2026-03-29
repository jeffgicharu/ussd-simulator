package com.ussd.controller;

import com.ussd.entity.SessionLog;
import com.ussd.entity.TransactionLog;
import com.ussd.repository.SessionLogRepository;
import com.ussd.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final SessionLogRepository sessionLogRepo;
    private final TransactionLogRepository txnLogRepo;

    @GetMapping("/sessions")
    public Map<String, Object> sessionAnalytics() {
        long total = sessionLogRepo.count();
        long lastHour = sessionLogRepo.countByStartedAtAfter(LocalDateTime.now().minusHours(1));
        long last24h = sessionLogRepo.countByStartedAtAfter(LocalDateTime.now().minusHours(24));
        Double avgDuration = sessionLogRepo.averageDurationMs();

        Map<String, Long> byOutcome = new LinkedHashMap<>();
        for (Object[] row : sessionLogRepo.countByOutcome()) {
            if (row[0] != null) byOutcome.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> dropOff = new LinkedHashMap<>();
        for (Object[] row : sessionLogRepo.dropOffByScreen()) {
            dropOff.put(row[0].toString(), (Long) row[1]);
        }

        return Map.of(
                "totalSessions", total,
                "sessionsLastHour", lastHour,
                "sessionsLast24Hours", last24h,
                "averageDurationMs", avgDuration != null ? Math.round(avgDuration) : 0,
                "byOutcome", byOutcome,
                "dropOffByScreen", dropOff
        );
    }

    @GetMapping("/transactions")
    public Map<String, Object> transactionAnalytics() {
        long total = txnLogRepo.count();
        BigDecimal volume = txnLogRepo.totalVolume();

        Map<String, Object> byType = new LinkedHashMap<>();
        for (Object[] row : txnLogRepo.aggregateByType()) {
            byType.put(row[0].toString(), Map.of("count", row[1], "volume", row[2]));
        }

        return Map.of(
                "totalTransactions", total,
                "totalVolume", volume,
                "byType", byType
        );
    }

    @GetMapping("/customer/{phone}")
    public Map<String, Object> customerHistory(@PathVariable String phone) {
        List<SessionLog> sessions = sessionLogRepo.findByPhoneNumberOrderByStartedAtDesc(phone);
        List<TransactionLog> transactions = txnLogRepo.findByPhoneNumberOrderByCreatedAtDesc(phone);

        return Map.of(
                "phone", phone,
                "totalSessions", sessions.size(),
                "totalTransactions", transactions.size(),
                "recentSessions", sessions.stream().limit(10).collect(Collectors.toList()),
                "recentTransactions", transactions.stream().limit(10).collect(Collectors.toList())
        );
    }
}
