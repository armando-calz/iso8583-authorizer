package io.github.armandocalz.authorizer.authorization;

/**
 * Serializes everything that happens to one transaction (its authorization, copies of it and its
 * reversals) for the rest of the current database transaction (ADR 0005).
 */
public interface TransactionKeyLock {

    void lock(TransactionKey key);
}
