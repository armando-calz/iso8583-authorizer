package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.authorization.TransactionKey;
import io.github.armandocalz.authorizer.authorization.TransactionKeyLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL transaction-scoped advisory lock on a 64-bit hash of the key. It is released on
 * commit or rollback, so it cannot leak. A hash collision only serializes two unrelated
 * transactions; it never mixes their data.
 */
@Component
class AdvisoryTransactionKeyLock implements TransactionKeyLock {

    private final JdbcClient jdbc;

    AdvisoryTransactionKeyLock(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock(TransactionKey key) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))")
                .param("key", key.toString())
                .query((rs, rowNum) -> null)
                .list();
    }
}
