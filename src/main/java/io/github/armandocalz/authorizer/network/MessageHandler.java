package io.github.armandocalz.authorizer.network;

import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.util.Optional;

/** Turns a decoded request into the response to send back, if any. */
@FunctionalInterface
public interface MessageHandler {

    Optional<IsoMessage> handle(IsoMessage request);
}
