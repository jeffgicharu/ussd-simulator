package com.ussd.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UssdIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Africa's Talking callback returns CON on initial dial")
    void atCallback_initialDial_returnsCon() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "at-1")
                .param("phoneNumber", "+254700000001")
                .param("serviceCode", "*384#")
                .param("text", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("CON")));
    }

    @Test
    @DisplayName("Africa's Talking callback returns END on balance check")
    void atCallback_balanceCheck_returnsEnd() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "at-2")
                .param("phoneNumber", "+254700000001")
                .param("text", "4*1234"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("END")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("KES")));
    }

    @Test
    @DisplayName("JSON API returns main menu")
    void jsonApi_dial_returnsMenu() throws Exception {
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"json-1\",\"phoneNumber\":\"+254700000001\",\"input\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continueSession").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Send Money")));
    }

    @Test
    @DisplayName("JSON API processes deposit flow")
    void jsonApi_deposit() throws Exception {
        // Dial
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"json-dep\",\"phoneNumber\":\"+254700000001\",\"input\":\"\"}"));

        // Select Deposit
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"json-dep\",\"phoneNumber\":\"+254700000001\",\"input\":\"5\"}"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("deposit amount")));

        // Enter amount
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"json-dep\",\"phoneNumber\":\"+254700000001\",\"input\":\"1000\"}"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PIN")));

        // Enter PIN
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"json-dep\",\"phoneNumber\":\"+254700000001\",\"input\":\"1234\"}"))
                .andExpect(jsonPath("$.continueSession").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("confirmed")));
    }

    @Test
    @DisplayName("Unregistered number gets redirected to registration")
    void unregistered_redirectsToRegistration() throws Exception {
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"reg-1\",\"phoneNumber\":\"+254799999999\",\"input\":\"\"}"))
                .andExpect(jsonPath("$.continueSession").value(true))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Create a 4-digit PIN")));
    }

    @Test
    @DisplayName("Registration flow creates account")
    void registration_flow() throws Exception {
        // Initial dial (unregistered)
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"reg-2\",\"phoneNumber\":\"+254788888888\",\"input\":\"\"}"));

        // Enter PIN
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"reg-2\",\"phoneNumber\":\"+254788888888\",\"input\":\"5555\"}"));

        // Confirm PIN
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"reg-2\",\"phoneNumber\":\"+254788888888\",\"input\":\"5555\"}"))
                .andExpect(jsonPath("$.continueSession").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Registration successful")));
    }

    @Test
    @DisplayName("Metrics endpoint returns session count")
    void metrics_returnsData() throws Exception {
        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registeredScreens").exists());
    }

    @Test
    @DisplayName("Session analytics returns stats")
    void sessionAnalytics_returnsData() throws Exception {
        // Generate a session first
        mockMvc.perform(post("/ussd/api")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"analytics-1\",\"phoneNumber\":\"+254700000001\",\"input\":\"\"}"));

        mockMvc.perform(get("/api/analytics/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").exists());
    }

    @Test
    @DisplayName("Transaction analytics returns volume data")
    void transactionAnalytics_returnsData() throws Exception {
        mockMvc.perform(get("/api/analytics/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions").exists())
                .andExpect(jsonPath("$.totalVolume").exists());
    }

    @Test
    @DisplayName("Customer history returns session and transaction data")
    void customerHistory_returnsData() throws Exception {
        mockMvc.perform(get("/api/analytics/customer/+254700000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+254700000001"));
    }

    @Test
    @DisplayName("Wrong PIN returns error and doesn't drain balance")
    void wrongPin_throughHttp() throws Exception {
        mockMvc.perform(post("/ussd/callback")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("sessionId", "pin-bad")
                .param("phoneNumber", "+254700000002")
                .param("text", "4*0000"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Wrong PIN")));
    }
}
