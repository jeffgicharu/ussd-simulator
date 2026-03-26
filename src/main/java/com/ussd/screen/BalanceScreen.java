package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BalanceScreen implements UssdScreen {

    private final WalletService walletService;

    @Override
    public String getId() { return "CHECK_BALANCE"; }

    @Override
    public UssdResponse render(UssdSession session) {
        return UssdResponse.con("Enter PIN to check balance:");
    }

    @Override
    public UssdResponse handleInput(UssdSession session, String input) {
        String pin = input.trim();
        if (!pin.matches("^[0-9]{4}$")) {
            return UssdResponse.con("Invalid PIN. Enter 4-digit PIN:");
        }

        String result = walletService.checkBalance(session.getPhoneNumber(), pin);

        session.end();
        return UssdResponse.end(result);
    }
}
