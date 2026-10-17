package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.authorization.AuthorizationDecision;
import io.github.armandocalz.authorizer.authorization.AuthorizationLog;
import io.github.armandocalz.authorizer.authorization.AuthorizationRequest;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuthorizationLog implements AuthorizationLog {

    private final JdbcClient jdbc;

    JdbcAuthorizationLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AuthorizationRequest request, String panToken, UUID accountId, AuthorizationDecision decision) {
        jdbc.sql("""
                INSERT INTO authorizations (pan_token, account_id, stan, rrn, terminal_id, transmission_date_time,
                                            amount_minor, currency, response_code, auth_code)
                VALUES (:token, :accountId, :stan, :rrn, :terminal, :transmitted, :amount, :currency, :responseCode, :authCode)
                """)
                .param("token", panToken)
                .param("accountId", accountId)
                .param("stan", request.stan())
                .param("rrn", request.rrn())
                .param("terminal", request.terminalId())
                .param("transmitted", request.transmissionDateTime())
                .param("amount", request.amountMinor())
                .param("currency", request.currency())
                .param("responseCode", decision.responseCode().code())
                .param("authCode", decision.authCode())
                .update();
    }
}
