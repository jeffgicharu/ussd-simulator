package com.ussd.integration;

import com.ussd.service.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Cumulative daily transfer limit (issue: no per-phone daily cap).
 * The limit is lowered to KES 1,000 here so the demo balances exercise
 * it deterministically; production defaults to KES 300,000. A fresh
 * context per test keeps the in-memory daily counters isolated.
 */
@SpringBootTest(properties = {
        "ussd.daily-transfer-limit=1000",
        // Own in-memory DB: @DirtiesContext tears this context down after
        // every method; create-drop must not wipe the schema other
        // contexts share (in-mem H2 is JVM-global by name).
        "spring.datasource.url=jdbc:h2:mem:daily-limit;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DailyTransferLimitIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WalletService walletService;

    private static final String SENDER = "+254700000001"; // PIN 1234, bal 75,000
    private static final String PIN = "1234";

    private String send(String sid, String amount) throws Exception {
        body(sid, "");
        body(sid, "1");
        body(sid, "0700000002");
        body(sid, amount);
        return mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, PIN)))
                .andReturn().getResponse().getContentAsString();
    }

    private void body(String sid, String input) throws Exception {
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, input)));
    }

    private static String json(String sid, String input) {
        return "{\"sessionId\":\"" + sid + "\",\"phoneNumber\":\"" + SENDER
                + "\",\"input\":\"" + input + "\"}";
    }

    @Test
    @DisplayName("Transfer exactly at the daily limit succeeds")
    void atLimit_succeeds() throws Exception {
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json("lim-at", "")));
        body("lim-at", "1");
        body("lim-at", "0700000002");
        body("lim-at", "1000");
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json("lim-at", PIN)))
                .andExpect(jsonPath("$.message").value(containsString("confirmed")));
    }

    @Test
    @DisplayName("Transfer just over the daily limit is rejected")
    void justOver_rejected() throws Exception {
        String resp = send("lim-over", "1001");
        org.assertj.core.api.Assertions.assertThat(resp)
                .contains("exceeded today's transfer limit");
    }

    @Test
    @DisplayName("Multiple smaller transfers are summed against the limit")
    void cumulativeTracking() throws Exception {
        String first = send("lim-c1", "600");   // total 600 — OK
        org.assertj.core.api.Assertions.assertThat(first).contains("confirmed");
        String second = send("lim-c2", "600");  // total 1200 — over 1000
        org.assertj.core.api.Assertions.assertThat(second)
                .contains("exceeded today's transfer limit");
    }

    @Test
    @DisplayName("Limit resets at the UTC day boundary")
    void resetsNextDay() throws Exception {
        Instant base = Instant.parse("2026-05-17T10:00:00Z");
        walletService.setClock(Clock.fixed(base, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThat(send("lim-d1", "1000"))
                .contains("confirmed");
        org.assertj.core.api.Assertions.assertThat(send("lim-d2", "10"))
                .contains("exceeded today's transfer limit");

        // Advance to the next UTC day — the counter is keyed by date.
        walletService.setClock(Clock.fixed(base.plus(Duration.ofDays(1)), ZoneOffset.UTC));
        org.assertj.core.api.Assertions.assertThat(send("lim-d3", "1000"))
                .contains("confirmed");
    }
}
