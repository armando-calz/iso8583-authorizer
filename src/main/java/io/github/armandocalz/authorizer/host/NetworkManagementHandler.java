package io.github.armandocalz.authorizer.host;

import io.github.armandocalz.authorizer.iso8583.Iso8583Fields;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import io.github.armandocalz.authorizer.network.MessageHandler;
import java.util.Optional;
import java.util.Set;

/**
 * {@code 0800} network management: sign-on (001), sign-off (002) and echo test (301), used
 * by terminals and acquirers to check that the link is alive. Answers {@code 0810}.
 */
public final class NetworkManagementHandler implements MessageHandler {

    static final String SIGN_ON = "001";
    static final String SIGN_OFF = "002";
    static final String ECHO_TEST = "301";

    private static final Set<String> SUPPORTED = Set.of(SIGN_ON, SIGN_OFF, ECHO_TEST);

    @Override
    public Optional<IsoMessage> handle(IsoMessage request) {
        String code = request.get(Iso8583Fields.NETWORK_MANAGEMENT_CODE).orElse("");
        ResponseCode result = SUPPORTED.contains(code) ? ResponseCode.APPROVED : ResponseCode.INVALID_TRANSACTION;
        return Optional.of(Responses.echoKeys(request, "0810")
                .set(Iso8583Fields.RESPONSE_CODE, result.code())
                .build());
    }
}
