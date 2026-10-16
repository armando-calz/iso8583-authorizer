package io.github.armandocalz.authorizer.network;

import java.util.Optional;

/**
 * Message type indicator helpers. The third digit is the message function: 0 request,
 * 1 request response, 2 advice, 3 advice response.
 */
public final class Mti {

    private Mti() {
    }

    /** The response MTI for a request or advice (e.g. 0100 -> 0110, 0420 -> 0430); empty otherwise. */
    public static Optional<String> responseTo(String mti) {
        if (mti == null || !mti.matches("[0-9]{4}")) {
            return Optional.empty();
        }
        char function = mti.charAt(2);
        if (function != '0' && function != '2') {
            return Optional.empty();
        }
        return Optional.of(mti.substring(0, 2) + (char) (function + 1) + mti.charAt(3));
    }
}
