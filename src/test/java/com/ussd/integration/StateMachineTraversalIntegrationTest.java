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
 * Multi-step traversal of the screen state machine: a full registered
 * send-money path, an unregistered self-registration path, invalid input
 * at representative menu nodes, and the (documented) absence of a
 * user-facing back command.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StateMachineTraversalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static String json(String sid, String phone, String input) {
        return "{\"sessionId\":\"" + sid + "\",\"phoneNumber\":\"" + phone
                + "\",\"input\":\"" + input + "\"}";
    }

    private void step(String sid, String phone, String input) throws Exception {
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, phone, input)));
    }

    @Test
    @DisplayName("Registered user traverses dial → send money → success END")
    void fullSendMoneyTraversal() throws Exception {
        String sid = "trav-send", phone = "+254700000001";
        step(sid, phone, "");                 // main menu
        step(sid, phone, "1");                // Send Money -> recipient
        step(sid, phone, "0700000002");       // recipient -> amount
        step(sid, phone, "500");              // amount -> confirm (PIN)
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, phone, "1234")))
                .andExpect(jsonPath("$.continueSession").value(false))
                .andExpect(jsonPath("$.message").value(containsString("confirmed")));
    }

    @Test
    @DisplayName("Unregistered user self-registers then reaches the main menu")
    void unregisteredSelfRegistrationThenMenu() throws Exception {
        String phone = "+254790000123";
        step("reg-a", phone, "");             // -> registration prompt
        step("reg-a", phone, "2468");         // create PIN
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json("reg-a", phone, "2468")))   // confirm
                .andExpect(jsonPath("$.continueSession").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Registration successful")));
        // New session, now registered -> main menu, not registration.
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json("reg-b", phone, "")))
                .andExpect(jsonPath("$.message").value(containsString("Welcome to M-Wallet")));
    }

    @ParameterizedTest
    @CsvSource({
            "inv-main,,9,Invalid choice",
            "inv-main2,,x,Invalid choice",
            "inv-acct,6,9,Invalid choice",
            "inv-lang,6*3,9,Invalid choice",
    })
    @DisplayName("Invalid input at a menu node re-prompts without corrupting state")
    void invalidInputAtMenuNodes(String sid, String pre, String bad, String expected)
            throws Exception {
        String phone = "+254700000001";
        step(sid, phone, "");
        if (pre != null && !pre.isEmpty()) {
            for (String p : pre.split("\\*")) {
                step(sid, phone, p);
            }
        }
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, phone, bad)))
                .andExpect(jsonPath("$.message").value(containsString(expected)));
    }

    @Test
    @DisplayName("No user-facing back command — '0' at a sub-screen is treated as that screen's input")
    void noBackNavigationCommand() throws Exception {
        String sid = "back-1", phone = "+254700000001";
        step(sid, phone, "");
        step(sid, phone, "1");                // SEND_MONEY_PHONE
        // "0" is not a back command; it fails the phone validator instead.
        mockMvc.perform(post("/ussd/api").contentType(MediaType.APPLICATION_JSON)
                .content(json(sid, phone, "0")))
                .andExpect(jsonPath("$.message").value(containsString("Invalid phone")));
    }
}
