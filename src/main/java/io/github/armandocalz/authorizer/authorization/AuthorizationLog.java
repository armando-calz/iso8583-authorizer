package io.github.armandocalz.authorizer.authorization;

import java.util.Optional;
import java.util.UUID;

/** Append-only record of every authorization decision. */
public interface AuthorizationLog {

    /**
     * @throws org.springframework.dao.DuplicateKeyException if the transaction key
     *         (terminal, RRN, STAN) is already recorded
     */
    void record(AuthorizationRequest request, String panToken, UUID accountId, AuthorizationDecision decision);

    Optional<RecordedAuthorization> findByTransactionKey(String terminalId, String rrn, String stan);
}
