package com.ussd.service;

import com.ussd.entity.TransactionLog;
import com.ussd.repository.TransactionLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles wallet operations for USSD flows.
 * Simulates wallets in-memory and logs every transaction to the database.
 */
@Service
@Slf4j
public class WalletService {

    @Autowired(required = false)
    private TransactionLogRepository txnLogRepo;

    // In-memory simulation state
    private final Map<String, BigDecimal> balances = new ConcurrentHashMap<>();
    private final Map<String, String> pins = new ConcurrentHashMap<>();
    private final Map<String, List<String>> transactionHistory = new ConcurrentHashMap<>();

    public WalletService() {
        // Seed demo accounts
        seedAccount("+254700000001", "1234", new BigDecimal("75000.00"));
        seedAccount("+254700000002", "5678", new BigDecimal("12500.00"));
        seedAccount("+254700000003", "4321", new BigDecimal("3200.00"));
    }

    private void seedAccount(String phone, String pin, BigDecimal balance) {
        balances.put(phone, balance);
        pins.put(phone, pin);
        transactionHistory.put(phone, Collections.synchronizedList(new ArrayList<>()));
    }

    // ─── OPERATIONS ─────────────────────────────────────────────────

    public String sendMoney(String senderPhone, String recipientPhone,
                            String amountStr, String feeStr, String pin) {
        if (!validatePin(senderPhone, pin)) {
            return "Transaction failed.\nWrong PIN entered.";
        }

        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal fee = new BigDecimal(feeStr);
        BigDecimal total = amount.add(fee);
        BigDecimal senderBalance = getBalance(senderPhone);

        if (senderBalance.compareTo(total) < 0) {
            return String.format(
                    "Transaction failed.\nInsufficient balance.\nAvailable: KES %s",
                    senderBalance.toPlainString());
        }

        // Ensure recipient exists
        balances.putIfAbsent(recipientPhone, BigDecimal.ZERO);
        transactionHistory.putIfAbsent(recipientPhone, Collections.synchronizedList(new ArrayList<>()));
        pins.putIfAbsent(recipientPhone, "0000");

        // Process transfer
        balances.compute(senderPhone, (k, v) -> v.subtract(total));
        balances.compute(recipientPhone, (k, v) -> v.add(amount));

        String txnId = generateTxnId();
        String timestamp = now();

        recordTransaction(senderPhone, String.format(
                "%s Sent KES %s to %s. Fee KES %s. Ref: %s",
                timestamp, amountStr, recipientPhone, feeStr, txnId));
        recordTransaction(recipientPhone, String.format(
                "%s Received KES %s from %s. Ref: %s",
                timestamp, amountStr, senderPhone, txnId));

        BigDecimal newBalance = getBalance(senderPhone);
        logTransaction(senderPhone, "SEND_MONEY", amount, fee, recipientPhone, "SUCCESS", newBalance);

        return String.format(
                "%s confirmed.\n" +
                "KES %s sent to %s.\n" +
                "Fee: KES %s\n" +
                "New balance: KES %s\n" +
                "Ref: %s",
                txnId, amountStr, recipientPhone, feeStr,
                newBalance.toPlainString(), txnId);
    }

    public String withdraw(String phone, String amountStr, String feeStr,
                           String agentNumber, String pin) {
        if (!validatePin(phone, pin)) {
            return "Transaction failed.\nWrong PIN entered.";
        }

        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal fee = new BigDecimal(feeStr);
        BigDecimal total = amount.add(fee);
        BigDecimal balance = getBalance(phone);

        if (balance.compareTo(total) < 0) {
            return String.format(
                    "Transaction failed.\nInsufficient balance.\nAvailable: KES %s",
                    balance.toPlainString());
        }

        balances.compute(phone, (k, v) -> v.subtract(total));

        String txnId = generateTxnId();
        recordTransaction(phone, String.format(
                "%s Withdrew KES %s at Agent %s. Fee KES %s. Ref: %s",
                now(), amountStr, agentNumber, feeStr, txnId));

        BigDecimal newBalance = getBalance(phone);
        logTransaction(phone, "WITHDRAWAL", amount, fee, "Agent:" + agentNumber, "SUCCESS", newBalance);

        return String.format(
                "%s confirmed.\n" +
                "KES %s withdrawn at Agent %s.\n" +
                "Fee: KES %s\n" +
                "New balance: KES %s",
                txnId, amountStr, agentNumber, feeStr,
                newBalance.toPlainString());
    }

    public String buyAirtime(String buyerPhone, String targetPhone,
                             String amountStr, String pin) {
        if (!validatePin(buyerPhone, pin)) {
            return "Transaction failed.\nWrong PIN entered.";
        }

        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal balance = getBalance(buyerPhone);

        if (balance.compareTo(amount) < 0) {
            return String.format(
                    "Transaction failed.\nInsufficient balance.\nAvailable: KES %s",
                    balance.toPlainString());
        }

        balances.compute(buyerPhone, (k, v) -> v.subtract(amount));

        String txnId = generateTxnId();
        String target = targetPhone.equals(buyerPhone) ? "self" : targetPhone;
        recordTransaction(buyerPhone, String.format(
                "%s Airtime KES %s for %s. Ref: %s",
                now(), amountStr, target, txnId));

        BigDecimal newBalance = getBalance(buyerPhone);
        logTransaction(buyerPhone, "AIRTIME", amount, BigDecimal.ZERO, targetPhone, "SUCCESS", newBalance);

        return String.format(
                "%s confirmed.\n" +
                "KES %s airtime purchased for %s.\n" +
                "New balance: KES %s",
                txnId, amountStr, targetPhone, newBalance.toPlainString());
    }

    public String checkBalance(String phone, String pin) {
        if (!validatePin(phone, pin)) {
            return "Failed. Wrong PIN entered.";
        }

        BigDecimal balance = getBalance(phone);
        return String.format(
                "Your M-Wallet balance is:\n" +
                "KES %s\n\n" +
                "As at %s",
                balance.toPlainString(), now());
    }

    public String getMiniStatement(String phone, String pin) {
        if (!validatePin(phone, pin)) {
            return "Failed. Wrong PIN entered.";
        }

        List<String> history = transactionHistory.getOrDefault(phone, List.of());
        BigDecimal balance = getBalance(phone);

        if (history.isEmpty()) {
            return String.format("No recent transactions.\nBalance: KES %s", balance.toPlainString());
        }

        StringBuilder sb = new StringBuilder("Mini Statement:\n");
        int start = Math.max(0, history.size() - 5);
        for (int i = history.size() - 1; i >= start; i--) {
            sb.append(history.get(i)).append("\n");
        }
        sb.append(String.format("\nBalance: KES %s", balance.toPlainString()));
        return sb.toString();
    }

    // ─── REGISTRATION ───────────────────────────────────────────────

    public boolean isRegistered(String phone) {
        return pins.containsKey(phone);
    }

    public String registerAccount(String phone, String pin) {
        if (pins.containsKey(phone)) {
            return "Phone number already registered.";
        }
        seedAccount(phone, pin, BigDecimal.ZERO);
        logTransaction(phone, "REGISTRATION", BigDecimal.ZERO, BigDecimal.ZERO, null, "SUCCESS", BigDecimal.ZERO);
        return "Registration successful!\nYour M-Wallet is now active.\nDial *384# to get started.";
    }

    // ─── DEPOSIT ────────────────────────────────────────────────────

    public String deposit(String phone, String amountStr, String pin) {
        if (!validatePin(phone, pin)) {
            return "Transaction failed.\nWrong PIN entered.";
        }

        BigDecimal amount = new BigDecimal(amountStr);
        BigDecimal before = getBalance(phone);
        balances.compute(phone, (k, v) -> v.add(amount));
        BigDecimal after = getBalance(phone);

        String txnId = generateTxnId();
        recordTransaction(phone, String.format("%s Deposit KES %s. Ref: %s", now(), amountStr, txnId));
        logTransaction(phone, "DEPOSIT", amount, BigDecimal.ZERO, null, "SUCCESS", after);

        return String.format("%s confirmed.\nKES %s deposited.\nNew balance: KES %s", txnId, amountStr, after.toPlainString());
    }

    // ─── HELPERS ────────────────────────────────────────────────────

    private BigDecimal getBalance(String phone) {
        return balances.getOrDefault(phone, BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ─── PIN VALIDATION WITH LOCKOUT ────────────────────────────────

    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();
    private static final int MAX_PIN_ATTEMPTS = 3;
    private static final long LOCKOUT_MS = 15 * 60 * 1000;

    public boolean validatePin(String phone, String pin) {
        Long lockExpiry = lockedUntil.get(phone);
        if (lockExpiry != null && System.currentTimeMillis() < lockExpiry) {
            return false;
        }

        String storedPin = pins.get(phone);
        if (storedPin != null && storedPin.equals(pin)) {
            failedAttempts.remove(phone);
            lockedUntil.remove(phone);
            return true;
        }

        int attempts = failedAttempts.merge(phone, 1, Integer::sum);
        if (attempts >= MAX_PIN_ATTEMPTS) {
            lockedUntil.put(phone, System.currentTimeMillis() + LOCKOUT_MS);
            log.warn("Account locked for {}: {} failed PIN attempts", phone, attempts);
        }
        return false;
    }

    public boolean isLocked(String phone) {
        Long lockExpiry = lockedUntil.get(phone);
        return lockExpiry != null && System.currentTimeMillis() < lockExpiry;
    }

    public int getRemainingAttempts(String phone) {
        return MAX_PIN_ATTEMPTS - failedAttempts.getOrDefault(phone, 0);
    }

    private void recordTransaction(String phone, String entry) {
        transactionHistory
                .computeIfAbsent(phone, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);
    }

    private void logTransaction(String phone, String type, BigDecimal amount,
                                BigDecimal fee, String counterparty, String status, BigDecimal balanceAfter) {
        if (txnLogRepo != null) {
            txnLogRepo.save(TransactionLog.builder()
                    .phoneNumber(phone)
                    .transactionType(type)
                    .amount(amount)
                    .fee(fee)
                    .counterparty(counterparty)
                    .reference(generateTxnId())
                    .status(status)
                    .balanceAfter(balanceAfter)
                    .channel("USSD")
                    .build());
        }
    }

    private String generateTxnId() {
        return "TXN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String now() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }
}
