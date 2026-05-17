package com.ussd.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused unit tests that kill HIGH-severity surviving mutants on the
 * money / auth paths surfaced by PIT (PIN lockout window + threshold,
 * deposit PIN guard, changePin guard + return value, duplicate
 * registration guard). These call {@link WalletService} directly so the
 * mutated branch is exercised precisely.
 */
class WalletServiceMutationTest {

    private static final String P1 = "+254700000001"; // seeded, PIN 1234
    private static final long LOCKOUT_MS = 15 * 60 * 1000;

    private WalletService ws;

    @BeforeEach
    void setUp() {
        ws = new WalletService();
    }

    // ── changePin guard (WalletService:261 RemoveConditional) ──
    @Test
    @DisplayName("changePin on an unknown phone returns false and creates nothing")
    void changePin_unknownPhone_returnsFalse() {
        assertThat(ws.changePin("+254000000000", "1111")).isFalse();
        assertThat(ws.validatePin("+254000000000", "1111")).isFalse();
        assertThat(ws.isRegistered("+254000000000")).isFalse();
    }

    // ── changePin return value (WalletService:270 BooleanFalseReturn) ──
    @Test
    @DisplayName("changePin succeeds, returns true, and the new PIN replaces the old")
    void changePin_success_returnsTrue_andPersists() {
        assertThat(ws.changePin(P1, "9999")).isTrue();
        assertThat(ws.validatePin(P1, "9999")).isTrue();
        assertThat(ws.validatePin(P1, "1234")).isFalse();
    }

    // ── deposit PIN guard (WalletService:276 RemoveConditional) ──
    @Test
    @DisplayName("deposit with the wrong PIN fails and leaves the balance unchanged")
    void deposit_wrongPin_failsAndBalanceUnchanged() {
        assertThat(ws.checkBalance(P1, "1234")).contains("75000.00");
        assertThat(ws.deposit(P1, "5000", "0000")).contains("Wrong PIN");
        assertThat(ws.checkBalance(P1, "1234")).contains("75000.00");
    }

    // ── deposit balance mutation (WalletService:282 lambda NullReturn) ──
    @Test
    @DisplayName("deposit with the correct PIN increases the balance by exactly the amount")
    void deposit_correctPin_increasesBalanceExactly() {
        assertThat(ws.deposit(P1, "5000", "1234")).contains("80000.00");
        assertThat(ws.checkBalance(P1, "1234")).contains("80000.00");
    }

    // ── lockout threshold (WalletService:320 RemoveConditional ORDER_IF) ──
    @Test
    @DisplayName("Two wrong attempts do not lock; the third does")
    void lockout_belowThreshold_doesNotLock() {
        assertThat(ws.validatePin(P1, "0000")).isFalse(); // attempt 1
        assertThat(ws.validatePin(P1, "0000")).isFalse(); // attempt 2
        // Still below MAX_PIN_ATTEMPTS — the correct PIN must work.
        assertThat(ws.validatePin(P1, "1234")).isTrue();
        assertThat(ws.isLocked(P1)).isFalse();
    }

    // ── lockout window + boundary (WalletService:308 Boundary + ORDER_IF) ──
    @Test
    @DisplayName("Three wrong attempts lock the account; the lock clears exactly at cooldown end")
    void lockout_locksThenExpiresAtBoundary() {
        Instant base = Instant.parse("2026-05-17T00:00:00Z");
        ws.setClock(Clock.fixed(base, ZoneOffset.UTC));

        for (int i = 0; i < 3; i++) {
            assertThat(ws.validatePin(P1, "0000")).isFalse();
        }
        // Locked: the correct PIN is rejected while the window is open.
        assertThat(ws.isLocked(P1)).isTrue();
        assertThat(ws.validatePin(P1, "1234")).isFalse();

        // 1 ms before cooldown end — still locked (boundary check).
        ws.setClock(Clock.fixed(base.plusMillis(LOCKOUT_MS - 1), ZoneOffset.UTC));
        assertThat(ws.validatePin(P1, "1234")).isFalse();

        // Exactly at cooldown end — lock has cleared, correct PIN works.
        ws.setClock(Clock.fixed(base.plusMillis(LOCKOUT_MS), ZoneOffset.UTC));
        assertThat(ws.isLocked(P1)).isFalse();
        assertThat(ws.validatePin(P1, "1234")).isTrue();
    }

    // ── duplicate registration guard (WalletService:248 RemoveConditional) ──
    @Test
    @DisplayName("Registering an already-registered number is rejected")
    void registerAccount_duplicate_rejected() {
        assertThat(ws.registerAccount(P1, "0000"))
                .isEqualTo("Phone number already registered.");
        // The original PIN must be untouched by the rejected re-registration.
        assertThat(ws.validatePin(P1, "1234")).isTrue();
    }
}
