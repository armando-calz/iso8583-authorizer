package io.github.armandocalz.authorizer.authorization;

/** Reversals received, matched to an authorization or not. At most one per original transaction. */
public interface ReversalLog {

    boolean existsFor(TransactionKey originalKey);

    void record(ReversalRequest request, String panToken, boolean originalFound);
}
