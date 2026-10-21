package io.github.armandocalz.authorizer.card;

import java.util.Optional;

public interface CardRepository {

    void save(Card card);

    Optional<Card> findByToken(String panToken);

    /**
     * Records {@code atc} as the card's highest transaction counter, atomically, if it is higher
     * than the last one seen. Returns {@code false} if it is not: the cryptogram is a replay.
     */
    boolean advanceAtc(String panToken, int atc);
}
