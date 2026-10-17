package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.authorization.AuthorizationDecision;
import io.github.armandocalz.authorizer.authorization.AuthorizationRequest;
import io.github.armandocalz.authorizer.authorization.AuthorizationService;
import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import io.github.armandocalz.authorizer.network.MessageHandler;
import java.util.Optional;

/** {@code 0100} authorization request: applies the issuer rules and answers {@code 0110}. */
public final class AuthorizationHandler implements MessageHandler {

    private final AuthorizationService service;

    public AuthorizationHandler(AuthorizationService service) {
        this.service = service;
    }

    @Override
    public Optional<IsoMessage> handle(IsoMessage request) {
        IsoMessage.Builder response = Responses.echoKeys(request, "0110");
        AuthorizationRequest parsed;
        try {
            parsed = AuthorizationRequest.from(request);
        } catch (IsoFormatException e) {
            return Optional.of(response.set(Iso8583Fields.RESPONSE_CODE, ResponseCode.FORMAT_ERROR.code()).build());
        }
        AuthorizationDecision decision = service.authorize(parsed);
        if (decision.authCode() != null) {
            response.set(Iso8583Fields.AUTHORIZATION_ID_RESPONSE, decision.authCode());
        }
        return Optional.of(response.set(Iso8583Fields.RESPONSE_CODE, decision.responseCode().code()).build());
    }
}
