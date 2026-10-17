package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.account.CreditAccount;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCreditAccountRepository implements CreditAccountRepository {

    private static final String SELECT = "SELECT id, currency, credit_limit_minor, available_minor FROM credit_accounts WHERE id = :id";

    private final JdbcClient jdbc;

    JdbcCreditAccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void create(CreditAccount account) {
        jdbc.sql("""
                INSERT INTO credit_accounts (id, currency, credit_limit_minor, available_minor)
                VALUES (:id, :currency, :limit, :available)
                """)
                .param("id", account.id())
                .param("currency", account.currency())
                .param("limit", account.creditLimitMinor())
                .param("available", account.availableMinor())
                .update();
    }

    @Override
    public Optional<CreditAccount> findById(UUID id) {
        return query(SELECT, id);
    }

    @Override
    public Optional<CreditAccount> lockById(UUID id) {
        return query(SELECT + " FOR UPDATE", id);
    }

    @Override
    public void updateAvailable(UUID id, long availableMinor) {
        jdbc.sql("UPDATE credit_accounts SET available_minor = :available WHERE id = :id")
                .param("available", availableMinor)
                .param("id", id)
                .update();
    }

    private Optional<CreditAccount> query(String sql, UUID id) {
        return jdbc.sql(sql)
                .param("id", id)
                .query((rs, rowNum) -> new CreditAccount(
                        rs.getObject("id", UUID.class),
                        rs.getString("currency"),
                        rs.getLong("credit_limit_minor"),
                        rs.getLong("available_minor")))
                .optional();
    }
}
