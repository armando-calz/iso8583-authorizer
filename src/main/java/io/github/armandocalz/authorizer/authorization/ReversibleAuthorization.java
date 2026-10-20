package io.github.armandocalz.authorizer.authorization;

import java.util.UUID;

/** What a reversal needs to know about the authorization it undoes. */
public record ReversibleAuthorization(
        long id, String panToken, UUID accountId, long amountMinor, boolean approved, boolean reversed) {
}
