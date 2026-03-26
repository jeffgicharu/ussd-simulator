package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LoansScreen {

    @Component
    public static class MenuScreen implements UssdScreen {
        @Override
        public String getId() { return "LOANS_MENU"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con(
                    "Loans & Savings\n" +
                    "1. Request Loan\n" +
                    "2. Repay Loan\n" +
                    "3. Check Loan Balance\n" +
                    "4. Savings Account"
            );
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            return switch (input.trim()) {
                case "1" -> {
                    session.navigateTo("LOAN_REQUEST_AMOUNT");
                    yield UssdResponse.con(
                            "Your loan limit: KES 50,000.00\n" +
                            "Enter loan amount:"
                    );
                }
                case "2" -> {
                    session.navigateTo("LOAN_REPAY_AMOUNT");
                    yield UssdResponse.con("Enter repayment amount (KES):");
                }
                case "3" -> {
                    session.end();
                    yield UssdResponse.end(
                            "Loan Balance\n" +
                            "Outstanding: KES 0.00\n" +
                            "Limit: KES 50,000.00\n" +
                            "Due date: N/A"
                    );
                }
                case "4" -> {
                    session.navigateTo("SAVINGS_MENU");
                    yield UssdResponse.con(
                            "Savings Account\n" +
                            "1. Deposit to Savings\n" +
                            "2. Withdraw from Savings\n" +
                            "3. Check Savings Balance"
                    );
                }
                default -> UssdResponse.con(
                        "Invalid choice.\n" +
                        "1. Request Loan\n" +
                        "2. Repay Loan\n" +
                        "3. Check Loan Balance\n" +
                        "4. Savings Account"
                );
            };
        }
    }

    @Component
    public static class LoanRequestAmountScreen implements UssdScreen {
        @Override
        public String getId() { return "LOAN_REQUEST_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter loan amount:");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input.trim());
            } catch (NumberFormatException e) {
                return UssdResponse.con("Invalid amount. Enter a number:");
            }

            if (amount.compareTo(new BigDecimal("100")) < 0) {
                return UssdResponse.con("Minimum loan is KES 100. Try again:");
            }
            if (amount.compareTo(new BigDecimal("50000")) > 0) {
                return UssdResponse.con("Exceeds your loan limit of KES 50,000. Try again:");
            }

            session.putData("loan_amount", amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
            session.navigateTo("LOAN_REQUEST_CONFIRM");
            return UssdResponse.con(String.format(
                    "Loan: KES %s\n" +
                    "Interest: 7.5%% (30 days)\n" +
                    "Repay: KES %s\n\n" +
                    "1. Accept\n" +
                    "2. Cancel",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString(),
                    amount.multiply(new BigDecimal("1.075")).setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));
        }
    }

    @Component
    public static class LoanRequestConfirmScreen implements UssdScreen {
        @Override
        public String getId() { return "LOAN_REQUEST_CONFIRM"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("1. Accept\n2. Cancel");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            session.end();
            return switch (input.trim()) {
                case "1" -> UssdResponse.end(String.format(
                        "Loan of KES %s approved!\n" +
                        "Funds sent to your M-Wallet.\n" +
                        "Repay within 30 days.",
                        session.getData("loan_amount")));
                case "2" -> UssdResponse.end("Loan request cancelled.");
                default -> UssdResponse.end("Invalid choice. Loan request cancelled.");
            };
        }
    }

    @Component
    public static class LoanRepayScreen implements UssdScreen {
        @Override
        public String getId() { return "LOAN_REPAY_AMOUNT"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con("Enter repayment amount (KES):");
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            BigDecimal amount;
            try {
                amount = new BigDecimal(input.trim());
            } catch (NumberFormatException e) {
                return UssdResponse.con("Invalid amount. Enter a number:");
            }

            session.end();
            return UssdResponse.end(String.format(
                    "Loan repayment of KES %s processed.\n" +
                    "Thank you.",
                    amount.setScale(2, RoundingMode.HALF_UP).toPlainString()
            ));
        }
    }

    @Component
    public static class SavingsMenuScreen implements UssdScreen {
        @Override
        public String getId() { return "SAVINGS_MENU"; }

        @Override
        public UssdResponse render(UssdSession session) {
            return UssdResponse.con(
                    "Savings Account\n" +
                    "1. Deposit to Savings\n" +
                    "2. Withdraw from Savings\n" +
                    "3. Check Savings Balance"
            );
        }

        @Override
        public UssdResponse handleInput(UssdSession session, String input) {
            session.end();
            return switch (input.trim()) {
                case "1" -> UssdResponse.end("Savings deposit feature coming soon.");
                case "2" -> UssdResponse.end("Savings withdrawal feature coming soon.");
                case "3" -> UssdResponse.end("Savings Balance: KES 0.00");
                default -> UssdResponse.end("Invalid choice.");
            };
        }
    }
}
