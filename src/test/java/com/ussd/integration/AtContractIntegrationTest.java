package com.ussd.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Africa's Talking webhook contract conformance: the CON/END prefix
 * convention, response length within USSD limits, and HTTP/content-type
 * correctness on the form-encoded callback.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AtContractIntegrationTest {

    /** Common USSD page budget used by several aggregators. */
    private static final int USSD_MAX_LEN = 182;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Continue responses are prefixed exactly with 'CON '")
    void conResponse_shapeCorrect() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "con-1")
                .param("phoneNumber", "+254700000001")
                .param("text", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("CON ")))
                .andExpect(content().string(containsString("Send Money")));
    }

    @Test
    @DisplayName("Terminal responses are prefixed 'END ' and end the session")
    void endResponse_shapeCorrect() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "end-1")
                .param("phoneNumber", "+254700000001")
                .param("text", "4*1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(startsWith("END ")));
        // Same sessionId is gone — a follow-up empty dial starts fresh.
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "end-1")
                .param("phoneNumber", "+254700000001")
                .param("text", ""))
                .andExpect(content().string(startsWith("CON ")));
    }

    @Test
    @DisplayName("Responses stay within the USSD length budget")
    void responseLength_withinLimit() throws Exception {
        String[][] cases = {
                {"len-menu", "+254700000001", ""},        // main menu
                {"len-reg", "+254791234567", ""},          // registration prompt
                {"len-acct", "+254700000001", "6"},        // My Account menu
                {"len-loan", "+254700000001", "7"},        // Loans & Savings menu
        };
        for (String[] c : cases) {
            MvcResult r = mockMvc.perform(post("/ussd/callback")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .param("sessionId", c[0])
                    .param("phoneNumber", c[1])
                    .param("text", c[2]))
                    .andExpect(status().isOk())
                    .andReturn();
            String body = r.getResponse().getContentAsString();
            assertThat(body.length())
                    .as("USSD response for %s must be <= %d chars", c[0], USSD_MAX_LEN)
                    .isLessThanOrEqualTo(USSD_MAX_LEN);
        }
    }

    @Test
    @DisplayName("Callback returns HTTP 200 with text/plain content type")
    void callback_httpAndContentType() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "ct-1")
                .param("phoneNumber", "+254700000001")
                .param("text", ""))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }
}
