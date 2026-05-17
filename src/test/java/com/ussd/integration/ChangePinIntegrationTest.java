package com.ussd.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Change-PIN flow: it must verify the current PIN through the shared
 * validation pipeline (lockout applies) and persist the new PIN so
 * subsequent money operations require it. Fresh context per test so the
 * in-memory PIN/lockout state is isolated.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ChangePinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String json(String sid, String phone, String input) {
        return "{\"sessionId\":\"" + sid + "\",\"phoneNumber\":\"" + phone
                + "\",\"input\":\"" + input + "\"}";
    }

    private String step(String sid, String phone, String input) throws Exception {
        return mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, phone, input)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("Wrong current PIN is rejected")
    void wrongCurrentPin_rejected() throws Exception {
        String phone = "+254700000001";
        step("cp-w", phone, "");
        step("cp-w", phone, "6");
        step("cp-w", phone, "2");
        assertThat(step("cp-w", phone, "0000"))
                .contains("Current PIN is incorrect");
    }

    @Test
    @DisplayName("Three wrong current-PIN attempts lock the account")
    void threeWrongCurrentPin_locksAccount() throws Exception {
        String phone = "+254700000002"; // PIN 5678
        for (int i = 1; i <= 3; i++) {
            String sid = "cp-lock-" + i;
            step(sid, phone, "");
            step(sid, phone, "6");
            step(sid, phone, "2");
            step(sid, phone, "0000");
        }
        // Account is now locked — even the correct PIN fails for money ops.
        step("cp-lock-bal", phone, "");
        step("cp-lock-bal", phone, "4");
        assertThat(step("cp-lock-bal", phone, "5678"))
                .contains("Wrong PIN");
    }

    @Test
    @DisplayName("Correct current PIN advances to new-PIN entry")
    void correctCurrentPin_advancesToNewPin() throws Exception {
        String phone = "+254700000001"; // PIN 1234
        step("cp-ok", phone, "");
        step("cp-ok", phone, "6");
        step("cp-ok", phone, "2");
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json("cp-ok", phone, "1234")))
                .andExpect(jsonPath("$.message").value(containsString("Enter new PIN")));
    }

    @Test
    @DisplayName("After change, the old PIN no longer works for money operations")
    void afterChange_oldPinRejectedForMoneyOps() throws Exception {
        String phone = "+254700000003"; // PIN 4321
        changePin(phone, "4321", "9876");

        // Send money using the OLD pin — must now be rejected.
        step("cp-old", phone, "");
        step("cp-old", phone, "1");
        step("cp-old", phone, "0700000002");
        step("cp-old", phone, "100");
        assertThat(step("cp-old", phone, "4321"))
                .contains("Wrong PIN");
    }

    @Test
    @DisplayName("After change, the new PIN is accepted for money operations")
    void afterChange_newPinAcceptedForMoneyOps() throws Exception {
        String phone = "+254700000003"; // PIN 4321
        changePin(phone, "4321", "9876");

        step("cp-new", phone, "");
        step("cp-new", phone, "1");
        step("cp-new", phone, "0700000002");
        step("cp-new", phone, "100");
        assertThat(step("cp-new", phone, "9876"))
                .contains("confirmed");
    }

    /** Drives the full change-PIN flow to completion. */
    private void changePin(String phone, String oldPin, String newPin) throws Exception {
        String sid = "cp-flow-" + phone;
        step(sid, phone, "");
        step(sid, phone, "6");          // My Account
        step(sid, phone, "2");          // Change PIN
        step(sid, phone, oldPin);       // current PIN -> verified
        step(sid, phone, newPin);       // new PIN
        assertThat(step(sid, phone, newPin)) // confirm
                .contains("PIN changed successfully");
    }
}
