package io.github.armandocalz.authorizer.card;

import java.util.Optional;

public interface CardRepository {

    void save(Card card);

    Optional<Card> findByToken(String panToken);
}
