package com.ussd.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Send-money edge cases: the per-transaction maximum boundary and the
 * tiered fee schedule. These stop at the confirm screen (no PIN entered),
 * so they assert validation/fee logic without mutating balances.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MoneyFlowEdgeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String json(String sid, String phone, String input) {
        return "{\"sessionId\":\"" + sid + "\",\"phoneNumber\":\"" + phone
                + "\",\"input\":\"" + input + "\"}";
    }

    private void step(String sid, String input) throws Exception {
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, "+254700000001", input)));
    }

    @Test
    @DisplayName("Amount exactly at the per-transaction max (500,000) is accepted")
    void amountAtMax_accepted() throws Exception {
        String sid = "max-ok";
        step(sid, "");
        step(sid, "1");
        step(sid, "0700000002");
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, "+254700000001", "500000")))
                .andExpect(jsonPath("$.message").value(containsString("Enter PIN to confirm")));
    }

    @Test
    @DisplayName("Amount just above the per-transaction max (500,001) is rejected")
    void amountAboveMax_rejected() throws Exception {
        String sid = "max-bad";
        step(sid, "");
        step(sid, "1");
        step(sid, "0700000002");
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, "+254700000001", "500001")))
                .andExpect(jsonPath("$.message").value(containsString("Maximum amount")));
    }

    @ParameterizedTest
    @CsvSource({
            "100,0",
            "500,7",
            "1000,13",
            "5000,57",
            "10000,90",
            "50000,108",
    })
    @DisplayName("Tiered send-money fee is computed correctly per band")
    void tieredFee_perBand(String amount, String expectedFee) throws Exception {
        String sid = "fee-" + amount;
        step(sid, "");
        step(sid, "1");
        step(sid, "0700000002");
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, "+254700000001", amount)))
                .andExpect(jsonPath("$.message").value(containsString("Fee: KES " + expectedFee)));
    }
}
