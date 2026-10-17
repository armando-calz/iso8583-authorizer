package io.github.armandocalz.authorizer.account;

import java.util.Objects;
import java.util.UUID;

/**
 * A credit line. {@code availableMinor} is the credit limit minus amounts held by approved
 * authorizations, in the currency's minor units.
 *
 * @param currency ISO 4217 numeric code, e.g. {@code 484} for MXN
 */
public record CreditAccount(UUID id, String currency, long creditLimitMinor, long availableMinor) {

    public CreditAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(currency, "currency");
        if (creditLimitMinor < 0 || availableMinor < 0) {
            throw new IllegalArgumentException("Credit limit and available credit cannot be negative");
        }
    }

    public static CreditAccount open(String currency, long creditLimitMinor) {
        return new CreditAccount(UUID.randomUUID(), currency, creditLimitMinor, creditLimitMinor);
    }

    public boolean canHold(long amountMinor) {
        return amountMinor <= availableMinor;
    }
}
