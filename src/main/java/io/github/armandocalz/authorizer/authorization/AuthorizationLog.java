package io.github.armandocalz.authorizer.authorization;

import java.util.UUID;

/** Append-only record of every authorization decision. */
public interface AuthorizationLog {

    void record(AuthorizationRequest request, String panToken, UUID accountId, AuthorizationDecision decision);
}
