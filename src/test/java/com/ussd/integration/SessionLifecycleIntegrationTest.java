package com.ussd.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Session lifecycle behaviour: expiry, the max-session cap, same-phone
 * concurrency, eviction on completion, and resumption within the window.
 * Each variant that needs a different timeout / cap runs in its own
 * Spring context via property overrides, so no real waiting is required.
 */
class SessionLifecycleIntegrationTest {

    private static String json(String sid, String phone, String input) {
        return "{\"sessionId\":\"" + sid + "\",\"phoneNumber\":\"" + phone
                + "\",\"input\":\"" + input + "\"}";
    }

    // ── Expiry: timeout = -1 forces every getSession() to treat the
    //    session as expired, so each request rebuilds a fresh session. ──
    @Nested
    @SpringBootTest(properties = {
            "ussd.session-timeout-seconds=-1",
            "spring.datasource.url=jdbc:h2:mem:sl-expiry;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    class Expiry {
        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("Expired session is rebuilt fresh on the next request")
        void expiredSession_rebuiltFresh() throws Exception {
            String sid = "exp-1", phone = "+254700000001";
            // dial -> main menu
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "")));
            // "1" -> on a fresh main-menu session this routes to Send Money
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "1")));
            // "2" -> if state had persisted we'd be at SEND_MONEY_PHONE and
            // get "Invalid phone number"; because the session keeps expiring
            // we are back at the main menu, so "2" routes to Withdraw.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "2")))
                    .andExpect(jsonPath("$.message").value(containsString("agent number")));
        }
    }

    // ── Max-session cap ──
    @Nested
    @SpringBootTest(properties = {
            "ussd.max-sessions=2",
            "spring.datasource.url=jdbc:h2:mem:sl-cap;DB_CLOSE_DELAY=-1"})
    @AutoConfigureMockMvc
    class Cap {
        @Autowired MockMvc mockMvc;

        /**
         * Inverted as part of closing issue #7: this previously
         * characterised the unhandled {@code IllegalStateException}; the
         * controller now converts a session-cap breach into a graceful
         * USSD {@code END} response.
         */
        @Test
        @DisplayName("Exceeding the session cap returns a graceful END, app survives")
        void overCap_returnsGracefulEnd() throws Exception {
            String phone = "+254700000001";
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("cap-1", phone, ""))).andExpect(status().isOk());
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("cap-2", phone, ""))).andExpect(status().isOk());
            // The third distinct session is over the cap of 2 — it now
            // gets a well-formed terminal USSD message, not a 5xx error.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("cap-3", phone, "")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.continueSession").value(false))
                    .andExpect(jsonPath("$.message")
                            .value(containsString("temporarily unavailable")));
            // The form-encoded callback gets the same graceful END.
            mockMvc.perform(post("/ussd/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("sessionId", "cap-4").param("phoneNumber", phone)
                    .param("text", ""))
                    .andExpect(status().isOk())
                    .andExpect(content().string(startsWith("END ")));
            // The app survives: an already-established session still works.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("cap-1", phone, "4")))
                    .andExpect(status().isOk());
        }
    }

    // ── Default-context behaviours (180s timeout) ──
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    class DefaultContext {
        @Autowired MockMvc mockMvc;

        @Test
        @DisplayName("Concurrent sessions for the same phone keep independent state")
        void samePhone_concurrentSessions_independent() throws Exception {
            String phone = "+254700000001";
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("conc-A", phone, "")));
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("conc-B", phone, "")));
            // A enters the send-money flow
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("conc-A", phone, "1")))
                    .andExpect(jsonPath("$.message").value(containsString("recipient phone")));
            // B independently enters the balance flow
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("conc-B", phone, "4")))
                    .andExpect(jsonPath("$.message").value(containsString("PIN to check balance")));
            // A is still in its own flow, unaffected by B
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json("conc-A", phone, "0700000002")))
                    .andExpect(jsonPath("$.message").value(containsString("amount")));
        }

        @Test
        @DisplayName("Session is evicted once a flow ends with END")
        void sessionEvicted_afterEnd() throws Exception {
            String sid = "evict-1", phone = "+254700000001";
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "")));
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "4")));
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "1234")))
                    .andExpect(jsonPath("$.continueSession").value(false));
            // Re-using the same sessionId now starts a brand-new session.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "")))
                    .andExpect(jsonPath("$.message").value(containsString("Welcome to M-Wallet")));
        }

        @Test
        @DisplayName("Session resumes at its current screen on re-dial within the window")
        void sessionResumes_withinWindow() throws Exception {
            String sid = "resume-1", phone = "+254700000001";
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "")));
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "1")));
            // Empty input re-renders the *current* screen, not the main menu.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "")))
                    .andExpect(jsonPath("$.message").value(containsString("recipient phone")));
            // And state persisted: "2" here is an invalid phone, not a menu route.
            mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                    .content(json(sid, phone, "2")))
                    .andExpect(jsonPath("$.message").value(containsString("Invalid phone")));
        }
    }
}
