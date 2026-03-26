package com.ussd.engine;

import com.ussd.model.UssdResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UssdEngineTest {

    @Autowired
    private UssdEngine engine;

    @Test
    @DisplayName("Should show main menu on initial dial")
    void initialDial_showsMainMenu() {
        UssdResponse response = engine.process("test-1", "+254700000001", "*384#", "");

        assertTrue(response.isContinueSession());
        assertTrue(response.getMessage().contains("Send Money"));
        assertTrue(response.getMessage().contains("Buy Airtime"));
        assertTrue(response.getMessage().contains("Check Balance"));
    }

    @Test
    @DisplayName("Should navigate to send money flow")
    void selectSendMoney_asksForPhone() {
        engine.process("test-2", "+254700000001", "*384#", "");
        UssdResponse response = engine.process("test-2", "+254700000001", "*384#", "1");

        assertTrue(response.isContinueSession());
        assertTrue(response.getMessage().contains("phone number"));
    }

    @Test
    @DisplayName("Should complete full send money flow")
    void sendMoney_fullFlow() {
        String sid = "test-send-full";
        String phone = "+254700000001";

        // Dial
        engine.process(sid, phone, "*384#", "");

        // Select "Send Money"
        engine.process(sid, phone, "*384#", "1");

        // Enter recipient phone
        engine.process(sid, phone, "*384#", "1*0700000002");

        // Enter amount
        UssdResponse amountResp = engine.process(sid, phone, "*384#", "1*0700000002*500");
        assertTrue(amountResp.isContinueSession());
        assertTrue(amountResp.getMessage().contains("500"));
        assertTrue(amountResp.getMessage().contains("PIN"));

        // Enter PIN
        UssdResponse result = engine.process(sid, phone, "*384#", "1*0700000002*500*1234");
        assertFalse(result.isContinueSession());
        assertTrue(result.getMessage().contains("confirmed"));
    }

    @Test
    @DisplayName("Should reject invalid phone number in send money")
    void sendMoney_invalidPhone_rejects() {
        String sid = "test-send-bad-phone";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "1");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "1*abc");
        assertTrue(response.isContinueSession());
        assertTrue(response.getMessage().toLowerCase().contains("invalid"));
    }

    @Test
    @DisplayName("Should check balance with correct PIN")
    void checkBalance_correctPin() {
        String sid = "test-balance";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "4");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "4*1234");
        assertFalse(response.isContinueSession());
        assertTrue(response.getMessage().contains("KES"));
    }

    @Test
    @DisplayName("Should reject wrong PIN on balance check")
    void checkBalance_wrongPin() {
        String sid = "test-balance-bad";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "4");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "4*9999");
        assertFalse(response.isContinueSession());
        assertTrue(response.getMessage().contains("Wrong PIN"));
    }

    @Test
    @DisplayName("Should navigate to airtime purchase for own phone")
    void buyAirtime_ownPhone() {
        String sid = "test-airtime";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "3");

        // Select "My Phone"
        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "3*1");
        assertTrue(response.isContinueSession());
        assertTrue(response.getMessage().contains("amount"));
    }

    @Test
    @DisplayName("Should show my phone number in My Account")
    void myAccount_showPhoneNumber() {
        String sid = "test-account";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "5");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "5*1");
        assertFalse(response.isContinueSession());
        assertTrue(response.getMessage().contains("+254700000001"));
    }

    @Test
    @DisplayName("Should handle invalid main menu choice")
    void mainMenu_invalidChoice() {
        String sid = "test-invalid";
        engine.process(sid, "+254700000001", "*384#", "");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "9");
        assertTrue(response.isContinueSession());
        assertTrue(response.getMessage().contains("Invalid"));
    }

    @Test
    @DisplayName("Should format response correctly for Africa's Talking")
    void africasTalking_formatCON() {
        UssdResponse response = engine.process("test-at", "+254700000001", "*384#", "");
        String at = response.toAfricasTalking();
        assertTrue(at.startsWith("CON "));
    }

    @Test
    @DisplayName("Should format END response for Africa's Talking")
    void africasTalking_formatEND() {
        String sid = "test-at-end";
        engine.process(sid, "+254700000001", "*384#", "");
        engine.process(sid, "+254700000001", "*384#", "5");

        UssdResponse response = engine.process(sid, "+254700000001", "*384#", "5*1");
        String at = response.toAfricasTalking();
        assertTrue(at.startsWith("END "));
    }

    @Test
    @DisplayName("Should handle shortcode input chain in one request")
    void shortcode_fullChain() {
        // Simulates *384*4*1234# — balance check in one go
        UssdResponse response = engine.process(
                "test-shortcode", "+254700000001", "*384#", "4*1234");
        assertFalse(response.isContinueSession());
        assertTrue(response.getMessage().contains("KES"));
    }
}
