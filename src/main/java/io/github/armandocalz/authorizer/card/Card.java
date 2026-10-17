package io.github.armandocalz.authorizer.card;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/** A card as the issuer stores it: tokenized PAN, BIN and last 4 digits only. */
public record Card(String panToken, String bin, String last4, YearMonth expiry, CardStatus status, UUID accountId) {

    public Card {
        Objects.requireNonNull(panToken, "panToken");
        Objects.requireNonNull(expiry, "expiry");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(accountId, "accountId");
    }

    public static Card issue(String pan, PanTokenizer tokenizer, YearMonth expiry, UUID accountId) {
        if (!Luhn.isValid(pan)) {
            throw new IllegalArgumentException("Card number fails the Luhn check");
        }
        return new Card(tokenizer.token(pan), pan.substring(0, 6), pan.substring(pan.length() - 4),
                expiry, CardStatus.ACTIVE, accountId);
    }

    /** Valid through the last day of its expiry month. */
    public boolean isExpiredOn(YearMonth month) {
        return month.isAfter(expiry);
    }
}
