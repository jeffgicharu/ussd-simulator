package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Multi-step send money flow:
 *   SEND_MONEY_PHONE  → enter recipient phone
 *   SEND_MONEY_AMOUNT → enter amount
 *   SEND_MONEY_CONFIRM → confirm with PIN
 */
@RequiredArgsConstructor
public class SendMoneyScreen {

    @Component
    public static class PhoneScreen implements UssdScreen {
        @Override
        public String getId() { return "SEND_MONEY_PHONE"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter recipient phone number:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            String phone = input.trim();

            if (!phone.matches("^\\+?[0-9]{10,15}$") && !phone.matches("^0[0-9]{9}$")) {
                return UssdResponse.con("Invalid phone number. Try again:");
            }

            // Normalize: 0712... → +254712...
            if (phone.startsWith("0")) {
                phone = "+254" + phone.substring(1);
            }

            if (phone.equals(session.getPhoneNumber())) {
                return UssdResponse.con("Cannot send to yourself. Enter another number:");
            }

            session.putData("recipient", phone);
            session.navigateTo("SEND_MONEY_AMOUNT");
            return UssdResponse.con("Enter amount (KES):");
        }
    }

    @Component
    public static class AmountScreen implements UssdScreen {
        @Override
        public String getId() { return "SEND_MONEY_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter amount (KES):");
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
                return UssdResponse.con("Minimum amount is KES 10. Try again:");
            }
            if (amount.compareTo(new BigDecimal("500000")) > 0) {
                return UssdResponse.con("Maximum amount is KES 500,000. Try again:");
            }

            session.putData("amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());

            BigDecimal fee = calculateFee(amount);
            session.putData("fee", fee.toPlainString());

            String recipient = session.getData("recipient");
            session.navigateTo("SEND_MONEY_CONFIRM");
            return UssdResponse.con(String.format(
                    "Send KES %s to %s?\n" +
                    "Fee: KES %s\n" +
                    "Total: KES %s\n\n" +
                    "Enter PIN to confirm:",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    recipient,
                    fee.toPlainString(),
                    amount.add(fee).toPlainString()
            ));
        }

        private BigDecimal calculateFee(BigDecimal amount) {
            // Tiered fee structure similar to M-Pesa
            List<BigDecimal[]> tiers = List.of(
                    new BigDecimal[]{new BigDecimal("100"), BigDecimal.ZERO},
                    new BigDecimal[]{new BigDecimal("500"), new BigDecimal("7")},
                    new BigDecimal[]{new BigDecimal("1000"), new BigDecimal("13")},
                    new BigDecimal[]{new BigDecimal("1500"), new BigDecimal("23")},
                    new BigDecimal[]{new BigDecimal("2500"), new BigDecimal("33")},
                    new BigDecimal[]{new BigDecimal("3500"), new BigDecimal("53")},
                    new BigDecimal[]{new BigDecimal("5000"), new BigDecimal("57")},
                    new BigDecimal[]{new BigDecimal("7500"), new BigDecimal("78")},
                    new BigDecimal[]{new BigDecimal("10000"), new BigDecimal("90")},
                    new BigDecimal[]{new BigDecimal("15000"), new BigDecimal("100")},
                    new BigDecimal[]{new BigDecimal("20000"), new BigDecimal("105")},
                    new BigDecimal[]{new BigDecimal("35000"), new BigDecimal("108")},
                    new BigDecimal[]{new BigDecimal("50000"), new BigDecimal("108")},
                    new BigDecimal[]{new BigDecimal("250000"), new BigDecimal("108")},
                    new BigDecimal[]{new BigDecimal("500000"), new BigDecimal("108")}
            );

            for (BigDecimal[] tier : tiers) {
                if (amount.compareTo(tier[0]) <= 0) {
                    return tier[1];
                }
            }
            return new BigDecimal("108");
        }
    }

    @Component
    @RequiredArgsConstructor
    public static class ConfirmScreen implements UssdScreen {

        private final WalletService walletService;

        @Override
        public String getId() { return "SEND_MONEY_CONFIRM"; }

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

            String recipient = session.getData("recipient");
            String amount = session.getData("amount");
            String fee = session.getData("fee");

            // Attempt the transfer
            String result = walletService.sendMoney(
                    session.getPhoneNumber(), recipient, amount, fee, pin);

            session.end();
            return UssdResponse.end(result);
        }
    }
}
