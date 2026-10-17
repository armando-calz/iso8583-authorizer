package io.github.armandocalz.authorizer.authorization;

import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import java.util.Objects;

/** The outcome of an authorization: a response code, plus an authorization code if approved. */
public record AuthorizationDecision(ResponseCode responseCode, String authCode) {

    public AuthorizationDecision {
        Objects.requireNonNull(responseCode, "responseCode");
        if (responseCode.isApproval() != (authCode != null)) {
            throw new IllegalArgumentException("Approvals, and only approvals, carry an authorization code");
        }
    }

    public static AuthorizationDecision approved(String authCode) {
        return new AuthorizationDecision(ResponseCode.APPROVED, authCode);
    }

    public static AuthorizationDecision declined(ResponseCode reason) {
        return new AuthorizationDecision(reason, null);
    }
}
