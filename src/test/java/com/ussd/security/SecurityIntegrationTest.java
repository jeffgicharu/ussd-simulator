package com.ussd.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * USSD-specific security suite: session hijacking/replay/fixation, PIN
 * brute-force + secrecy, injection/fuzzing, cross-user isolation, and
 * sensitive-data exposure. Drives the real engine + H2 through the
 * webhook, exactly as an attacker would.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String body(String sid, String phone, String input) {
        return "{\"sessionId\":\"" + sid.replace("\"", "\\\"")
                + "\",\"phoneNumber\":\"" + phone
                + "\",\"input\":\"" + input + "\"}";
    }

    private String api(String sid, String phone, String input) throws Exception {
        return mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                        .content(body(sid, phone, input)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ─────────────────── Session security ───────────────────
    @Nested
    class SessionSecurity {

        @Test
        @DisplayName("Session bound to its phone — another caller cannot continue it")
        void sessionHijack_rejected() throws Exception {
            String sid = "hj-1";
            // Victim A establishes a session and is mid-flow.
            api(sid, "+254700000001", "");
            api(sid, "+254700000001", "4");          // at CHECK_BALANCE
            // Attacker B replays the victim's sessionId.
            String stolen = api(sid, "+254700000099", "1234");
            assertThat(stolen).contains("Session error");
            assertThat(stolen).doesNotContain("75000");   // no victim balance
        }

        @Test
        @DisplayName("Caller-supplied session IDs do not share state across sessions")
        void distinctSessions_isolated() throws Exception {
            api("iso-A", "+254700000001", "");
            api("iso-A", "+254700000001", "1");        // A -> SEND_MONEY_PHONE
            String b = api("iso-B", "+254700000001", "4"); // B fresh -> menu route
            assertThat(b).contains("PIN to check balance");
        }

        @Test
        @DisplayName("Replaying an ended session's terminal step does not move money")
        void sessionReplay_noEffect() throws Exception {
            String sid = "rp-1", p = "+254700000001";
            api(sid, p, "");
            api(sid, p, "1");
            api(sid, p, "0700000002");
            api(sid, p, "10");
            api(sid, p, "1234");                       // END — session consumed
            // Replaying just the final PIN on the (now gone) session id:
            String replay = api(sid, p, "1234");
            assertThat(replay).doesNotContain("confirmed");
            assertThat(replay).contains("Invalid choice"); // fresh MAIN_MENU
        }

        @Test
        @DisplayName("Session-id injection payloads are handled safely")
        void sessionIdInjection_safe() throws Exception {
            for (String evil : new String[]{
                    "a\\nInjected: true", "'; DROP TABLE session_logs;--",
                    "<script>alert(1)</script>", "x".repeat(10_000)}) {
                mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                                .content(body(evil, "+254700000001", "")))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("Concurrent sessions for the same phone stay independent")
        void concurrentSamePhone_partitioned() throws Exception {
            api("cc-A", "+254700000001", "");
            api("cc-B", "+254700000001", "");
            api("cc-A", "+254700000001", "1");
            String b = api("cc-B", "+254700000001", "4");
            assertThat(b).contains("PIN to check balance");
            String a = api("cc-A", "+254700000001", "0700000002");
            assertThat(a).contains("amount");
        }
    }

    @Nested
    @SpringBootTest(properties = "ussd.session-timeout-seconds=-1")
    @AutoConfigureMockMvc
    class Expiry {
        @Autowired MockMvc mvc;

        @Test
        @DisplayName("An expired session cannot be resumed")
        void expiredSession_notReusable() throws Exception {
            String sid = "exp-s", p = "+254700000001";
            mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(body(sid, p, "")));
            mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(body(sid, p, "1")));
            // Session keeps expiring -> "2" routes from a fresh MAIN_MENU
            // (Withdraw), proving the old state was not resumed.
            mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                            .content(body(sid, p, "2")))
                    .andExpect(jsonPath("$.message").value(containsString("agent number")));
        }
    }

    // ─────────────────── PIN security ───────────────────
    @Nested
    class PinSecurity {

        @Test
        @DisplayName("Brute force locks the account and a new session stays locked")
        void bruteForce_lockNotBypassableByNewSession() throws Exception {
            String p = "+254700000002";                // PIN 5678
            for (int i = 0; i < 3; i++) {
                String s = "bf-" + i;
                api(s, p, "");
                api(s, p, "4");
                api(s, p, "0000");
            }
            // Brand-new session, correct PIN — still locked.
            api("bf-new", p, "");
            api("bf-new", p, "4");
            assertThat(api("bf-new", p, "5678")).contains("Wrong PIN");
        }

        @Test
        @DisplayName("Concurrent wrong-PIN attempts still trip the lockout (no race)")
        void bruteForce_concurrentNoRace() throws Exception {
            String p = "+254700000003";                // PIN 4321
            ExecutorService ex = Executors.newFixedThreadPool(10);
            List<Future<?>> fs = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                final int n = i;
                fs.add(ex.submit(() -> {
                    try {
                        String s = "race-" + n;
                        api(s, p, "");
                        api(s, p, "4");
                        api(s, p, "1111");
                    } catch (Exception ignored) { }
                }));
            }
            for (Future<?> f : fs) f.get();
            ex.shutdown();
            api("race-check", p, "");
            api("race-check", p, "4");
            assertThat(api("race-check", p, "4321")).contains("Wrong PIN");
        }

        @Test
        @DisplayName("PINs never reach the logs")
        void pin_notLogged() throws Exception {
            Logger root = (Logger) LoggerFactory.getLogger(
                    org.slf4j.Logger.ROOT_LOGGER_NAME);
            ListAppender<ILoggingEvent> app = new ListAppender<>();
            app.start();
            root.addAppender(app);
            try {
                mvc.perform(post("/ussd/callback")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("sessionId", "log-1")
                        .param("phoneNumber", "+254700000001")
                        .param("text", "4*1234"));
                api("log-2", "+254700000001", "");
                api("log-2", "+254700000001", "4");
                api("log-2", "+254700000001", "1234");
            } finally {
                root.detachAppender(app);
            }
            for (ILoggingEvent e : app.list) {
                assertThat(e.getFormattedMessage())
                        .as("log line must not contain a PIN")
                        .doesNotContain("1234");
            }
        }

        @Test
        @DisplayName("The entered PIN is not echoed in the response")
        void pin_notEchoedInResponse() throws Exception {
            String sid = "echo-1", p = "+254700000001";
            api(sid, p, "");
            api(sid, p, "4");
            assertThat(api(sid, p, "1234")).doesNotContain("1234");
        }

        @Test
        @DisplayName("After a PIN change the old PIN is rejected (regression)")
        void oldPinRejectedAfterChange() throws Exception {
            String p = "+254700000001";
            String s = "chg-1";
            api(s, p, ""); api(s, p, "6"); api(s, p, "2");
            api(s, p, "1234");        // current
            api(s, p, "8765");        // new
            api(s, p, "8765");        // confirm -> persisted
            // Old PIN must no longer authorise a money operation.
            String s2 = "chg-2";
            api(s2, p, ""); api(s2, p, "4");
            assertThat(api(s2, p, "1234")).contains("Wrong PIN");
        }
    }

    // ─────────────────── Input / state machine ───────────────────
    @Nested
    class InputHandling {

        @Test
        @DisplayName("SQL-injection payloads in phone/text do not error or leak")
        void sqlInjection_safe() throws Exception {
            String r = api("sql-1", "' OR '1'='1", "");
            assertThat(r).doesNotContain("SQL").doesNotContain("Exception");
            mvc.perform(post("/ussd/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("sessionId", "sql-2")
                    .param("phoneNumber", "+254700000001")
                    .param("text", "1';DROP TABLE transaction_logs;--"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Script / JNDI payloads are treated as inert input")
        void scriptPayloads_inert() throws Exception {
            for (String evil : new String[]{
                    "<script>alert(1)</script>", "${jndi:ldap://x/a}",
                    "{{7*7}}", "%n%n%n"}) {
                String r = api("xss-" + evil.hashCode(), "+254700000001", evil);
                assertThat(r).doesNotContain("Exception");
            }
        }

        @Test
        @DisplayName("A 10k-char input is handled without a server error")
        void veryLongInput_bounded() throws Exception {
            api("long-1", "+254700000001", "9".repeat(10_000));
        }

        @Test
        @DisplayName("Negative amount in send-money is rejected")
        void negativeAmount_rejected() throws Exception {
            String s = "neg-1", p = "+254700000001";
            api(s, p, ""); api(s, p, "1"); api(s, p, "0700000002");
            assertThat(api(s, p, "-500")).contains("Minimum amount");
        }

        @Test
        @DisplayName("Non-numeric where numeric expected gives a clean error, no stack trace")
        void nonNumeric_cleanError() throws Exception {
            String s = "nn-1", p = "+254700000001";
            api(s, p, ""); api(s, p, "1"); api(s, p, "0700000002");
            String r = api(s, p, "abc");
            assertThat(r).contains("Invalid amount");
            assertThat(r).doesNotContain("Exception").doesNotContain("\tat ");
        }

        @ParameterizedTest
        @ValueSource(longs = {1L, 7L, 42L, 1337L, 99999L})
        @DisplayName("State-machine fuzzing never produces a server error")
        void fuzz_noServerErrors(long seed) throws Exception {
            Random rnd = new Random(seed);
            String[] alphabet = {"", "0", "1", "2", "7", "9", "1234",
                    "0700000002", "abc", "*", "#", "-1", "999999999"};
            String sid = "fuzz-" + seed;
            for (int i = 0; i < 25; i++) {
                String in = alphabet[rnd.nextInt(alphabet.length)];
                mvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                                .content(body(sid, "+25470000000" + (rnd.nextInt(3) + 1), in)))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("State cannot be skipped — PIN entry cannot be forged")
        void skipState_cannotForgePin() throws Exception {
            // Sending a PIN to a fresh session lands on MAIN_MENU, not a
            // confirm screen — no money moves.
            String r = api("skip-1", "+254700000001", "1234");
            assertThat(r).contains("Invalid choice");
            assertThat(r).doesNotContain("confirmed");
        }
    }

    // ─────────────────── Cross-user isolation ───────────────────
    @Nested
    class CrossUserIsolation {

        @Test
        @DisplayName("Cannot read another account's balance without its PIN")
        void cannotReadOthersBalance() throws Exception {
            String s = "xu-bal", attacker = "+254700000001";
            api(s, attacker, ""); api(s, attacker, "4");
            // Attacker guesses victim's account is +2547..002; but the
            // session is bound to the attacker's own phone, and balance
            // still needs *this* account's PIN.
            assertThat(api(s, attacker, "5678")).contains("Wrong PIN");
        }

        @Test
        @DisplayName("Cannot read another account's mini-statement")
        void cannotReadOthersStatement() throws Exception {
            String s = "xu-mini", p = "+254700000002";
            api(s, p, ""); api(s, p, "6"); api(s, p, "5");
            assertThat(api(s, p, "1234")).contains("Wrong PIN"); // not 5678
        }

        @Test
        @DisplayName("Cannot operate on another account by swapping the phone mid-session")
        void cannotOperateOnOthersAccount() throws Exception {
            String s = "xu-op";
            api(s, "+254700000002", "");
            api(s, "+254700000002", "2");                 // withdraw flow
            String hijack = api(s, "+254700000001", "12345");
            assertThat(hijack).contains("Session error");
        }
    }

    // ─────────────────── Sensitive data / webhook ───────────────────
    @Nested
    class SensitiveData {

        @Test
        @DisplayName("Malformed JSON yields no stack trace in the response")
        void malformedJson_noStackTrace() throws Exception {
            String r = mvc.perform(post("/ussd/api")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not-json"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(r).doesNotContain("\tat ")
                    .doesNotContain("com.ussd")
                    .doesNotContain("Exception:");
        }

        @Test
        @DisplayName("Normal responses do not expose internal session state")
        void noInternalStateLeak() throws Exception {
            String r = api("leak-1", "+254700000001", "");
            assertThat(r).doesNotContain("sessionId")
                    .doesNotContain("failedAttempts")
                    .doesNotContain("lockedUntil");
        }

        @Test
        @DisplayName("Unauthenticated USSD webhook accepts requests (documented design)")
        void webhookHasNoSignature_documented() throws Exception {
            // The Africa's Talking gateway is not integrated in this demo;
            // there is no shared secret to verify. Documented in
            // SECURITY_TESTING.md as an accepted, scoped design decision.
            mvc.perform(post("/ussd/callback")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("sessionId", "nosig-1")
                            .param("phoneNumber", "+254700000001")
                            .param("text", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("Exception"))));
        }
    }
}
