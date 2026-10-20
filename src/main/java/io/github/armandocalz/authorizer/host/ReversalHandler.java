package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.authorization.ReversalRequest;
import io.github.armandocalz.authorizer.authorization.ReversalService;
import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import io.github.armandocalz.authorizer.network.MessageHandler;
import java.util.Optional;

/** {@code 0400} reversal (and {@code 0401} repeat): answers {@code 0410}. */
public final class ReversalHandler implements MessageHandler {

    private final ReversalService service;

    public ReversalHandler(ReversalService service) {
        this.service = service;
    }

    @Override
    public Optional<IsoMessage> handle(IsoMessage request) {
        ResponseCode result;
        try {
            result = service.reverse(ReversalRequest.from(request));
        } catch (IsoFormatException e) {
            result = ResponseCode.FORMAT_ERROR;
        }
        return Optional.of(Responses.echoKeys(request, "0410")
                .set(Iso8583Fields.RESPONSE_CODE, result.code())
                .build());
    }
}
