package com.ussd.util;

/**
 * Neutralises untrusted values before they reach a log line.
 *
 * <p>USSD callers fully control {@code sessionId}, {@code phoneNumber} and
 * the raw input/{@code text}. Without sanitisation a caller can inject
 * CR/LF (and other control characters) to forge log records
 * (CWE-117 / log injection), and the cumulative AT {@code text} chain
 * embeds the caller's PIN (e.g. {@code "4*1234"}) which must never be
 * persisted to logs (CWE-532, sensitive data exposure).
 */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** Max length kept in a log field — bounds log volume from huge inputs. */
    private static final int MAX = 64;

    /**
     * Strip CR/LF and other control characters and bound the length, so a
     * value is safe to interpolate into a single log line.
     */
    public static String clean(String value) {
        if (value == null) {
            return "null";
        }
        String stripped = value.replaceAll("[\\p{Cntrl}]", "_");
        if (stripped.length() > MAX) {
            stripped = stripped.substring(0, MAX) + "...(" + value.length() + ")";
        }
        return stripped;
    }

    /**
     * Sanitise <em>and</em> mask digit runs. USSD input is mostly digits
     * (menu choices, amounts, and PINs) and we cannot tell a PIN apart
     * from a menu choice at the controller, so any run of 2+ digits is
     * masked. Single digits (menu navigation) are kept for debuggability.
     */
    public static String maskInput(String value) {
        if (value == null) {
            return "null";
        }
        return clean(value).replaceAll("\\d{2,}", "***");
    }
}
