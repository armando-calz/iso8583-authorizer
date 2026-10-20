package io.github.armandocalz.authorizer.authorization;

import java.util.Objects;

/** Identifies a card transaction from the acquirer's side: terminal id, RRN and STAN (ADR 0004). */
public record TransactionKey(String terminalId, String rrn, String stan) {

    public TransactionKey {
        Objects.requireNonNull(terminalId, "terminalId");
        Objects.requireNonNull(rrn, "rrn");
        Objects.requireNonNull(stan, "stan");
    }

    @Override
    public String toString() {
        return terminalId + "/" + rrn + "/" + stan;
    }
}
