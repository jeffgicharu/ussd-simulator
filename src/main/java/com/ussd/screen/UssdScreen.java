package com.ussd.screen;

import com.ussd.model.UssdResponse;
import com.ussd.model.UssdSession;

/**
 * Each screen in the USSD menu tree implements this interface.
 * A screen renders its display text and processes user input.
 */
public interface UssdScreen {

    /**
     * Unique identifier for this screen.
     */
    String getId();

    /**
     * Render the display text for this screen.
     * Called when the user navigates TO this screen.
     */
    UssdResponse render(UssdSession session);

    /**
     * Process user input and return the next response.
     * May navigate to another screen via session.navigateTo().
     */
    UssdResponse handleInput(UssdSession session, String input);
}
