package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DepositScreen {

    @Component
    public static class AmountScreen implements UssdScreen {
        @Override public String getId() { return "DEPOSIT_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter deposit amount (KES):");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input.trim());
            } catch (NumberFormatException e) {
                return UssdResponse.con("Invalid amount. Enter a number:");
            }
            if (amount.compareTo(new BigDecimal("10")) < 0) {
                return UssdResponse.con("Minimum deposit is KES 10. Try again:");
            }
            if (amount.compareTo(new BigDecimal("300000")) > 0) {
                return UssdResponse.con("Maximum deposit is KES 300,000. Try again:");
            }

            session.putData("deposit_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            session.navigateTo("DEPOSIT_CONFIRM");
            return UssdResponse.con(String.format("Deposit KES %s to your M-Wallet?\n\nEnter PIN to confirm:",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ConfirmScreen implements UssdScreen {

        private final WalletService walletService;

        @Override public String getId() { return "DEPOSIT_CONFIRM"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter PIN to confirm deposit:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String pin = input.trim();
            if (!pin.matches("^[0-9]{4}$")) {
                return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
            }
            String amount = session.getData("deposit_amount");
            String result = walletService.deposit(session.getPhoneNumber(), amount, pin);
            session.end();
            return UssdResponse.end(result);
        }
    }
}
