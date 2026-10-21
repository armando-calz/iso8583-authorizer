package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.authorization.AuthorizationDecision;
import io.github.armandocalz.authorizer.authorization.AuthorizationRequest;
import io.github.armandocalz.authorizer.authorization.AuthorizationService;
import io.github.armandocalz.authorizer.emv.EmvCryptography;
import io.github.armandocalz.authorizer.emv.IccData;
import io.github.armandocalz.authorizer.emv.Tlv;
import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import io.github.armandocalz.authorizer.network.MessageHandler;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * {@code 0100} authorization request: applies the issuer rules and answers {@code 0110}. For chip
 * transactions with a valid ARQC, the response carries the ARPC in field 55 (tag 91) so the card
 * can authenticate the issuer.
 */
public final class AuthorizationHandler implements MessageHandler {

    private final AuthorizationService service;
    private final EmvCryptography emv;

    public AuthorizationHandler(AuthorizationService service, EmvCryptography emv) {
        this.service = service;
        this.emv = emv;
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
        if (parsed.hasIccData() && emv.verifyArqc(parsed.pan(), parsed.iccData())) {
            byte[] issuerAuthentication = emv.issuerAuthenticationData(parsed.pan(), parsed.iccData(), decision.responseCode().code());
            response.set(Iso8583Fields.ICC_DATA, HexFormat.of().withUpperCase().formatHex(
                    Tlv.encode(Map.of(IccData.ISSUER_AUTHENTICATION_DATA, issuerAuthentication))));
        }
        return Optional.of(response.set(Iso8583Fields.RESPONSE_CODE, decision.responseCode().code()).build());
    }
}
