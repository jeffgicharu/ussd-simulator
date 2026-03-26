package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
public class MyAccountScreen {

    @Component
    public static class MenuScreen implements UssdScreen {
        @Override
        public String getId() { return "MY_ACCOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con(
                    "My Account\n" +
                    "1. My Phone Number\n" +
                    "2. Change PIN\n" +
                    "3. Language\n" +
                    "4. Full Statement\n" +
                    "5. Mini Statement"
            );
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            return switch (input.trim()) {
                case "1" -> {
                    session.end();
                    yield UssdResponse.end("Your phone number is:\n" + session.getPhoneNumber());
                }
                case "2" -> {
                    session.navigateTo("CHANGE_PIN_OLD");
                    yield UssdResponse.con("Enter current PIN:");
                }
                case "3" -> {
                    session.navigateTo("LANGUAGE");
                    yield UssdResponse.con(
                            "Select Language:\n" +
                            "1. English\n" +
                            "2. Kiswahili"
                    );
                }
                case "4" -> {
                    session.navigateTo("FULL_STATEMENT");
                    yield UssdResponse.con(
                            "Full statement will be sent via SMS.\n" +
                            "Enter PIN to confirm:"
                    );
                }
                case "5" -> {
                    session.navigateTo("MINI_STATEMENT");
                    yield UssdResponse.con("Enter PIN for mini statement:");
                }
                default -> UssdResponse.con(
                        "Invalid choice.\n" +
                        "1. My Phone Number\n" +
                        "2. Change PIN\n" +
                        "3. Language\n" +
                        "4. Full Statement\n" +
                        "5. Mini Statement"
                );
            };
        }
    }

    @Component
    public static class ChangePinOldScreen implements UssdScreen {
        @Override
        public String getId() { return "CHANGE_PIN_OLD"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter current PIN:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            if (!input.trim().matches("^[0-9]{4}$")) {
                return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
            }
            session.putData("old_pin", input.trim());
            session.navigateTo("CHANGE_PIN_NEW");
            return UssdResponse.con("Enter new PIN:");
        }
    }

    @Component
    public static class ChangePinNewScreen implements UssdScreen {
        @Override
        public String getId() { return "CHANGE_PIN_NEW"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter new PIN:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            if (!input.trim().matches("^[0-9]{4}$")) {
                return UssdResponse.con("New PIN must be 4 digits. Try again:");
            }
            session.putData("new_pin", input.trim());
            session.navigateTo("CHANGE_PIN_CONFIRM");
            return UssdResponse.con("Confirm new PIN:");
        }
    }

    @Component
    public static class ChangePinConfirmScreen implements UssdScreen {
        @Override
        public String getId() { return "CHANGE_PIN_CONFIRM"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Confirm new PIN:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String newPin = session.getData("new_pin");
            if (!input.trim().equals(newPin)) {
                session.end();
                return UssdResponse.end("PINs do not match. PIN change cancelled.");
            }
            session.end();
            return UssdResponse.end("PIN changed successfully.");
        }
    }

    @Component
    public static class LanguageScreen implements UssdScreen {
        @Override
        public String getId() { return "LANGUAGE"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Select Language:\n1. English\n2. Kiswahili");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            session.end();
            return switch (input.trim()) {
                case "1" -> UssdResponse.end("Language set to English.");
                case "2" -> UssdResponse.end("Lugha imewekwa Kiswahili.");
                default -> UssdResponse.end("Invalid choice. Language unchanged.");
            };
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class MiniStatementScreen implements UssdScreen {

        private final WalletService walletService;

        @Override
        public String getId() { return "MINI_STATEMENT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter PIN for mini statement:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            if (!input.trim().matches("^[0-9]{4}$")) {
                return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
            }

            String result = walletService.getMiniStatement(session.getPhoneNumber(), input.trim());
            session.end();
            return UssdResponse.end(result);
        }
    }

    @Component
    public static class FullStatementScreen implements UssdScreen {
        @Override
        public String getId() { return "FULL_STATEMENT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter PIN to confirm:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            if (!input.trim().matches("^[0-9]{4}$")) {
                return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
            }
            session.end();
            return UssdResponse.end(
                    "Full statement request received.\n" +
                    "It will be sent to " + session.getPhoneNumber() + " via SMS."
            );
        }
    }
}
