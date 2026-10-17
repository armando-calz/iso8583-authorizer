package io.github.armandocalz.authorizer.account;

import java.util.Optional;
import java.util.UUID;

public interface CreditAccountRepository {

    void create(CreditAccount account);

    Optional<CreditAccount> findById(UUID id);

    /** Reads the account and locks it until the current transaction ends. */
    Optional<CreditAccount> lockById(UUID id);

    void updateAvailable(UUID id, long availableMinor);
}
