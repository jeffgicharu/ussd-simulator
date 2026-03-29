package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

public class RegisterScreen {

    @Component
    public static class PinScreen implements UssdScreen {
        @Override public String getId() { return "REGISTER_PIN"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Welcome! Create a 4-digit PIN to register:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            if (!input.trim().matches("^[0-9]{4}$")) {
                return UssdResponse.con("PIN must be exactly 4 digits. Try again:");
            }
            session.putData("new_pin", input.trim());
            session.navigateTo("REGISTER_CONFIRM");
            return UssdResponse.con("Confirm your PIN:");
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ConfirmScreen implements UssdScreen {

        private final WalletService walletService;

        @Override public String getId() { return "REGISTER_CONFIRM"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Confirm your PIN:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String newPin = session.getData("new_pin");
            if (!input.trim().equals(newPin)) {
                session.end();
                return UssdResponse.end("PINs do not match.\nRegistration cancelled.");
            }
            String result = walletService.registerAccount(session.getPhoneNumber(), newPin);
            session.end();
            return UssdResponse.end(result);
        }
    }
}
