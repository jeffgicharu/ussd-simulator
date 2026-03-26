package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@RequiredArgsConstructor
public class WithdrawScreen {

    @Component
    public static class AgentScreen implements UssdScreen {
        @Override
        public String getId() { return "WITHDRAW_AGENT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter agent number:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String agent = input.trim();
            if (!agent.matches("^[0-9]{5,10}$")) {
                return UssdResponse.con("Invalid agent number. Try again:");
            }
            session.putData("agent", agent);
            session.navigateTo("WITHDRAW_AMOUNT");
            return UssdResponse.con("Enter amount to withdraw (KES):");
        }
    }

    @Component
    public static class AmountScreen implements UssdScreen {
        @Override
        public String getId() { return "WITHDRAW_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter amount to withdraw (KES):");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input.trim());
            } catch (NumberFormatException e) {
                return UssdResponse.con("Invalid amount. Enter a number:");
            }

            if (amount.compareTo(new BigDecimal("50")) < 0) {
                return UssdResponse.con("Minimum withdrawal is KES 50. Try again:");
            }
            if (amount.compareTo(new BigDecimal("150000")) > 0) {
                return UssdResponse.con("Maximum withdrawal is KES 150,000. Try again:");
            }

            session.putData("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());

            BigDecimal fee = new BigDecimal("33");
            session.putData("fee", fee.toPlainString());

            session.navigateTo("WITHDRAW_CONFIRM");
            return UssdResponse.con(String.format(
                    "Withdraw KES %s from Agent %s?\n" +
                    "Fee: KES %s\n\n" +
                    "Enter PIN to confirm:",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    session.getData("agent"),
                    fee.toPlainString()
            ));
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ConfirmScreen implements UssdScreen {

        private final WalletService walletService;

        @Override
        public String getId() { return "WITHDRAW_CONFIRM"; }

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

            String amount = session.getData("amount");
            String agent = session.getData("agent");
            String fee = session.getData("fee");

            String result = walletService.withdraw(
                    session.getPhoneNumber(), amount, fee, agent, pin);

            session.end();
            return UssdResponse.end(result);
        }
    }
}
