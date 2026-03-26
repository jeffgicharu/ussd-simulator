package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public class AirtimeScreen {

    @Component
    public static class MenuScreen implements UssdScreen {
        @Override
        public String getId() { return "AIRTIME_MENU"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con(
                    "Buy Airtime\n" +
                    "1. My Phone\n" +
                    "2. Other Number"
            );
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            return switch (input.trim()) {
                case "1" -> {
                    session.putData("airtime_target", session.getPhoneNumber());
                    session.navigateTo("AIRTIME_AMOUNT");
                    yield UssdResponse.con("Enter airtime amount (KES):");
                }
                case "2" -> {
                    session.navigateTo("AIRTIME_OTHER_PHONE");
                    yield UssdResponse.con("Enter phone number:");
                }
                default -> UssdResponse.con("Invalid choice.\n1. My Phone\n2. Other Number");
            };
        }
    }

    @Component
    public static class OtherPhoneScreen implements UssdScreen {
        @Override
        public String getId() { return "AIRTIME_OTHER_PHONE"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter phone number:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String phone = input.trim();
            if (!phone.matches("^(\\+?254|0)[0-9]{9}$")) {
                return UssdResponse.con("Invalid phone number. Try again:");
            }
            if (phone.startsWith("0")) {
                phone = "+254" + phone.substring(1);
            }
            session.putData("airtime_target", phone);
            session.navigateTo("AIRTIME_AMOUNT");
            return UssdResponse.con("Enter airtime amount (KES):");
        }
    }

    @Component
    public static class AmountScreen implements UssdScreen {
        @Override
        public String getId() { return "AIRTIME_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter airtime amount (KES):");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input.trim());
            } catch (NumberFormatException e) {
                return UssdResponse.con("Invalid amount. Enter a number:");
            }

            if (amount.compareTo(new BigDecimal("5")) < 0) {
                return UssdResponse.con("Minimum airtime is KES 5. Try again:");
            }
            if (amount.compareTo(new BigDecimal("10000")) > 0) {
                return UssdResponse.con("Maximum airtime is KES 10,000. Try again:");
            }

            session.putData("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            String target = session.getData("airtime_target");

            session.navigateTo("AIRTIME_CONFIRM");
            return UssdResponse.con(String.format(
                    "Buy KES %s airtime for %s?\n\n" +
                    "Enter PIN to confirm:",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    target
            ));
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ConfirmScreen implements UssdScreen {

        private final WalletService walletService;

        @Override
        public String getId() { return "AIRTIME_CONFIRM"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter PIN to confirm:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String pin = input.trim();
            if (!pin.matches("^[0-9]{4}$")) {
                return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
            }

            String target = session.getData("airtime_target");
            String amount = session.getData("amount");

            String result = walletService.buyAirtime(
                    session.getPhoneNumber(), target, amount, pin);

            session.end();
            return UssdResponse.end(result);
        }
    }
}
