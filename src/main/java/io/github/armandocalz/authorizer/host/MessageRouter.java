package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import io.github.armandocalz.authorizer.network.MessageHandler;
import io.github.armandocalz.authorizer.network.Mti;
import java.util.Map;
import java.util.Optional;

/**
 * Dispatches requests to a handler by MTI. A request or advice with an unsupported MTI is
 * answered with response code 12 (invalid transaction); anything else unexpected, such as a
 * response sent to the host, is dropped.
 */
public final class MessageRouter implements MessageHandler {

    private final Map<String, MessageHandler> handlers;

    public MessageRouter(Map<String, MessageHandler> handlers) {
        this.handlers = Map.copyOf(handlers);
    }

    @Override
    public Optional<IsoMessage> handle(IsoMessage request) {
        MessageHandler handler = handlers.get(request.mti());
        if (handler != null) {
            return handler.handle(request);
        }
        return Mti.responseTo(request.mti()).map(responseMti -> Responses.echoKeys(request, responseMti)
                .set(Iso8583Fields.RESPONSE_CODE, ResponseCode.INVALID_TRANSACTION.code())
                .build());
    }
}
