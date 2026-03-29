package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import org.springframework.stereotype.Component;

@Component
public class MainMenuScreen implements UssdScreen {

    @Override
    public String getId() {
        return "MAIN_MENU";
    }

    @Override
    public UssdResponse render(UssdSession session) {
        return UssdResponse.con(
                "Welcome to M-Wallet\n" +
                "1. Send Money\n" +
                "2. Withdraw Cash\n" +
                "3. Buy Airtime\n" +
                "4. Check Balance\n" +
                "5. Deposit\n" +
                "6. My Account\n" +
                "7. Loans & Savings"
        );
    }

    @Override
    public UssdResponse handleInput(UssdSession session, String input) {
        return switch (input.trim()) {
            case "1" -> {
                session.navigateTo("SEND_MONEY_PHONE");
                yield UssdResponse.con("Enter recipient phone number:");
            }
            case "2" -> {
                session.navigateTo("WITHDRAW_AGENT");
                yield UssdResponse.con("Enter agent number:");
            }
            case "3" -> {
                session.navigateTo("AIRTIME_MENU");
                yield UssdResponse.con(
                        "Buy Airtime\n" +
                        "1. My Phone\n" +
                        "2. Other Number"
                );
            }
            case "4" -> {
                session.navigateTo("CHECK_BALANCE");
                yield UssdResponse.con("Enter PIN to check balance:");
            }
            case "5" -> {
                session.navigateTo("DEPOSIT_AMOUNT");
                yield UssdResponse.con("Enter deposit amount (KES):");
            }
            case "6" -> {
                session.navigateTo("MY_ACCOUNT");
                yield UssdResponse.con(
                        "My Account\n" +
                        "1. My Phone Number\n" +
                        "2. Change PIN\n" +
                        "3. Language\n" +
                        "4. Full Statement\n" +
                        "5. Mini Statement"
                );
            }
            case "7" -> {
                session.navigateTo("LOANS_MENU");
                yield UssdResponse.con(
                        "Loans & Savings\n" +
                        "1. Request Loan\n" +
                        "2. Repay Loan\n" +
                        "3. Check Loan Balance\n" +
                        "4. Savings Account"
                );
            }
            default -> UssdResponse.con(
                    "Invalid choice. Try again:\n" +
                    "1. Send Money\n" +
                    "2. Withdraw Cash\n" +
                    "3. Buy Airtime\n" +
                    "4. Check Balance\n" +
                    "5. Deposit\n" +
                    "6. My Account\n" +
                    "7. Loans & Savings"
            );
        };
    }
}
