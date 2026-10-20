package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.authorization.ReversalLog;
import io.github.armandocalz.authorizer.authorization.ReversalRequest;
import io.github.armandocalz.authorizer.authorization.TransactionKey;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcReversalLog implements ReversalLog {

    private final JdbcClient jdbc;

    JdbcReversalLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean existsFor(TransactionKey originalKey) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM reversals
                               WHERE terminal_id = :terminal AND rrn = :rrn AND original_stan = :stan)
                """)
                .param("terminal", originalKey.terminalId())
                .param("rrn", originalKey.rrn())
                .param("stan", originalKey.stan())
                .query(Boolean.class)
                .single();
    }

    @Override
    public void record(ReversalRequest request, String panToken, boolean originalFound) {
        jdbc.sql("""
                INSERT INTO reversals (terminal_id, rrn, original_stan, stan, transmission_date_time,
                                       pan_token, amount_minor, original_found)
                VALUES (:terminal, :rrn, :originalStan, :stan, :transmitted, :token, :amount, :found)
                """)
                .param("terminal", request.terminalId())
                .param("rrn", request.rrn())
                .param("originalStan", request.originalStan())
                .param("stan", request.stan())
                .param("transmitted", request.transmissionDateTime())
                .param("token", panToken)
                .param("amount", request.amountMinor())
                .param("found", originalFound)
                .update();
    }
}
