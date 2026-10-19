package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.authorization.AuthorizationDecision;
import io.github.armandocalz.authorizer.authorization.AuthorizationLog;
import io.github.armandocalz.authorizer.authorization.AuthorizationRequest;
import io.github.armandocalz.authorizer.authorization.RecordedAuthorization;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import java.util.Optional;
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
                                            processing_code, amount_minor, currency, response_code, auth_code)
                VALUES (:token, :accountId, :stan, :rrn, :terminal, :transmitted,
                        :processingCode, :amount, :currency, :responseCode, :authCode)
                """)
                .param("token", panToken)
                .param("accountId", accountId)
                .param("stan", request.stan())
                .param("rrn", request.rrn())
                .param("terminal", request.terminalId())
                .param("transmitted", request.transmissionDateTime())
                .param("processingCode", request.processingCode())
                .param("amount", request.amountMinor())
                .param("currency", request.currency())
                .param("responseCode", decision.responseCode().code())
                .param("authCode", decision.authCode())
                .update();
    }

    @Override
    public Optional<RecordedAuthorization> findByTransactionKey(String terminalId, String rrn, String stan) {
        return jdbc.sql("""
                SELECT pan_token, processing_code, amount_minor, currency, response_code, auth_code
                FROM authorizations
                WHERE terminal_id = :terminal AND rrn = :rrn AND stan = :stan
                """)
                .param("terminal", terminalId)
                .param("rrn", rrn)
                .param("stan", stan)
                .query((rs, rowNum) -> new RecordedAuthorization(
                        rs.getString("pan_token"),
                        rs.getString("processing_code"),
                        rs.getLong("amount_minor"),
                        rs.getString("currency"),
                        new AuthorizationDecision(
                                ResponseCode.fromCode(rs.getString("response_code")).orElseThrow(),
                                rs.getString("auth_code"))))
                .optional();
    }
}
