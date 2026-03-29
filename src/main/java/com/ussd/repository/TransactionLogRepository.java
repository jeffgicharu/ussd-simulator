package com.ussd.repository;

import com.ussd.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, Long> {
    List<TransactionLog> findByPhoneNumberOrderByCreatedAtDesc(String phoneNumber);

    List<TransactionLog> findByReferenceOrderByCreatedAtDesc(String reference);

    @Query("SELECT t.transactionType, COUNT(t), COALESCE(SUM(t.amount), 0) FROM TransactionLog t GROUP BY t.transactionType")
    List<Object[]> aggregateByType();

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionLog t WHERE t.status = 'SUCCESS'")
    BigDecimal totalVolume();
}
