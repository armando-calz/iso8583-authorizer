package io.github.armandocalz.authorizer.infrastructure.persistence;

import io.github.armandocalz.authorizer.card.Card;
import io.github.armandocalz.authorizer.card.CardRepository;
import io.github.armandocalz.authorizer.card.CardStatus;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCardRepository implements CardRepository {

    private final JdbcClient jdbc;

    JdbcCardRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Card card) {
        jdbc.sql("""
                INSERT INTO cards (pan_token, bin, last4, expiry, status, account_id)
                VALUES (:token, :bin, :last4, :expiry, :status, :accountId)
                ON CONFLICT (pan_token) DO UPDATE SET expiry = EXCLUDED.expiry, status = EXCLUDED.status
                """)
                .param("token", card.panToken())
                .param("bin", card.bin())
                .param("last4", card.last4())
                .param("expiry", "%02d%02d".formatted(card.expiry().getYear() % 100, card.expiry().getMonthValue()))
                .param("status", card.status().name())
                .param("accountId", card.accountId())
                .update();
    }

    @Override
    public Optional<Card> findByToken(String panToken) {
        return jdbc.sql("SELECT pan_token, bin, last4, expiry, status, account_id FROM cards WHERE pan_token = :token")
                .param("token", panToken)
                .query((rs, rowNum) -> {
                    String expiry = rs.getString("expiry");
                    return new Card(
                            rs.getString("pan_token"),
                            rs.getString("bin"),
                            rs.getString("last4"),
                            YearMonth.of(2000 + Integer.parseInt(expiry.substring(0, 2)), Integer.parseInt(expiry.substring(2))),
                            CardStatus.valueOf(rs.getString("status")),
                            rs.getObject("account_id", UUID.class));
                })
                .optional();
    }

    @Override
    public boolean advanceAtc(String panToken, int atc) {
        return jdbc.sql("UPDATE cards SET last_atc = :atc WHERE pan_token = :token AND (last_atc IS NULL OR last_atc < :atc)")
                .param("atc", atc)
                .param("token", panToken)
                .update() == 1;
    }
}
