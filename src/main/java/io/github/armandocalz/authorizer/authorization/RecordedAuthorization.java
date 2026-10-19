package io.github.armandocalz.authorizer.authorization;

/** An authorization already in the log, as needed to recognize and answer a retransmission. */
public record RecordedAuthorization(
        String panToken, String processingCode, long amountMinor, String currency, AuthorizationDecision decision) {

    /** Whether {@code request} is the same transaction (not just the same key). */
    public boolean matches(AuthorizationRequest request, String requestPanToken) {
        return panToken.equals(requestPanToken)
                && processingCode.equals(request.processingCode())
                && amountMinor == request.amountMinor()
                && currency.equals(request.currency());
    }
}
