package com.ussd.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_logs", indexes = {
    @Index(name = "idx_txnlog_phone", columnList = "phoneNumber"),
    @Index(name = "idx_txnlog_type", columnList = "transactionType"),
    @Index(name = "idx_txnlog_ref", columnList = "reference")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 15)
    private String phoneNumber;

    @Column(nullable = false, length = 20)
    private String transactionType;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    private BigDecimal fee;

    private String counterparty;

    @Column(nullable = false, length = 30)
    private String reference;

    @Column(nullable = false, length = 10)
    private String status;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    private String channel;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
