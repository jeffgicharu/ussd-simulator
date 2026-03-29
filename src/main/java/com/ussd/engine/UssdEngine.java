package com.ussd.engine;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;
import com.ussd.screen.UssdScreen;
import com.ussd.service.WalletService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Core USSD processing engine.
 * Routes requests to the appropriate screen based on session state.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UssdEngine {

    private final SessionManager sessionManager;
    private final List<UssdScreen> screenBeans;
    private final WalletService walletService;

    private final Map<String, UssdScreen> screens = new HashMap<>();

    @PostConstruct
    void init() {
        for (UssdScreen screen : screenBeans) {
            screens.put(screen.getId(), screen);
            log.info("Registered USSD screen: {}", screen.getId());
        }
        log.info("Total screens registered: {}", screens.size());
    }

    /**
     * Process a USSD request in Africa's Talking format.
     *
     * @param sessionId   unique session ID from the telco gateway
     * @param phoneNumber caller's phone number
     * @param serviceCode dialed USSD code (e.g., *384#)
     * @param text        cumulative input chain, e.g., "" for initial, "1" for first input,
     *                    "1*2" for second input after choosing option 1, etc.
     */
    public UssdResponse process(String sessionId, String phoneNumber,
                                String serviceCode, String text) {

        // Parse the input chain
        String[] inputs = (text == null || text.isEmpty())
                ? new String[0]
                : text.split("\\*");

        UssdSession session = sessionManager.getSession(sessionId);

        if (session == null) {
            session = sessionManager.createSession(sessionId, phoneNumber, serviceCode);

            // Redirect unregistered users to registration
            if (!walletService.isRegistered(phoneNumber)) {
                session.navigateTo("REGISTER_PIN");
                if (inputs.length == 0) {
                    return getScreen("REGISTER_PIN").render(session);
                }
                return processInputChain(session, inputs);
            }

            if (inputs.length == 0) {
                return getScreen(session.getCurrentScreenId()).render(session);
            }

            // Some gateways send all inputs at once for shortcodes
            // e.g., *384*1*0712345678*5000# sends text="1*0712345678*5000"
            return processInputChain(session, inputs);
        }

        // Existing session — process the latest input only
        if (inputs.length == 0) {
            return getScreen(session.getCurrentScreenId()).render(session);
        }

        // The latest input is the last element in the chain
        String latestInput = inputs[inputs.length - 1];
        return processInput(session, latestInput);
    }

    /**
     * Process for JSON API format (single input per request).
     */
    public UssdResponse processStep(String sessionId, String phoneNumber,
                                    String serviceCode, String input) {
        UssdSession session = sessionManager.getSession(sessionId);

        if (session == null) {
            session = sessionManager.createSession(sessionId, phoneNumber, serviceCode);
            if (!walletService.isRegistered(phoneNumber)) {
                session.navigateTo("REGISTER_PIN");
                if (input == null || input.isEmpty()) {
                    return getScreen("REGISTER_PIN").render(session);
                }
            } else if (input == null || input.isEmpty()) {
                return getScreen(session.getCurrentScreenId()).render(session);
            }
        }

        if (input == null || input.isEmpty()) {
            return getScreen(session.getCurrentScreenId()).render(session);
        }

        return processInput(session, input);
    }

    private UssdResponse processInputChain(UssdSession session, String[] inputs) {
        UssdResponse response = null;
        for (String input : inputs) {
            response = processInput(session, input);
            if (!response.isContinueSession()) {
                sessionManager.endSession(session.getSessionId());
                return response;
            }
        }
        return response;
    }

    private UssdResponse processInput(UssdSession session, String input) {
        UssdScreen screen = getScreen(session.getCurrentScreenId());
        UssdResponse response = screen.handleInput(session, input);

        if (!response.isContinueSession()) {
            sessionManager.endSession(session.getSessionId());
        }

        return response;
    }

    private UssdScreen getScreen(String screenId) {
        UssdScreen screen = screens.get(screenId);
        if (screen == null) {
            throw new IllegalStateException("Unknown screen: " + screenId);
        }
        return screen;
    }

    public int getScreenCount() {
        return screens.size();
    }
}
