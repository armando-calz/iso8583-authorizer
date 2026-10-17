package io.github.armandocalz.authorizer.authorization;

import io.github.armandocalz.authorizer.account.CreditAccount;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import io.github.armandocalz.authorizer.card.Card;
import io.github.armandocalz.authorizer.card.CardRepository;
import io.github.armandocalz.authorizer.card.CardStatus;
import io.github.armandocalz.authorizer.card.Luhn;
import io.github.armandocalz.authorizer.card.PanTokenizer;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issuer authorization rules, applied in order; the first that fails decides the response code.
 *
 * <ol>
 *   <li>Purchase processing code only, otherwise 12 (invalid transaction)</li>
 *   <li>PAN passes the Luhn check, otherwise 14 (invalid card number)</li>
 *   <li>Card exists, otherwise 14</li>
 *   <li>Card is active, otherwise 62 (restricted card)</li>
 *   <li>Card is not expired, otherwise 54 (expired card)</li>
 *   <li>Expiry date sent matches the card's, otherwise 05 (do not honor)</li>
 *   <li>Amount is positive, otherwise 13 (invalid amount)</li>
 *   <li>Currency matches the account's, otherwise 57 (transaction not permitted)</li>
 *   <li>Available credit covers the amount, otherwise 51 (insufficient funds)</li>
 * </ol>
 *
 * An approval holds the amount against the account's available credit. The account row is
 * locked while checking and holding, so concurrent authorizations cannot overspend it.
 */
@Service
public class AuthorizationService {

    private final CardRepository cards;
    private final CreditAccountRepository accounts;
    private final AuthorizationLog log;
    private final PanTokenizer tokenizer;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthorizationService(
            CardRepository cards, CreditAccountRepository accounts, AuthorizationLog log, PanTokenizer tokenizer, Clock clock) {
        this.cards = cards;
        this.accounts = accounts;
        this.log = log;
        this.tokenizer = tokenizer;
        this.clock = clock;
    }

    @Transactional
    public AuthorizationDecision authorize(AuthorizationRequest request) {
        String panToken = tokenizer.token(request.pan());
        Optional<Card> card = Luhn.isValid(request.pan()) ? cards.findByToken(panToken) : Optional.empty();
        UUID accountId = card.map(Card::accountId).orElse(null);

        AuthorizationDecision decision = decide(request, card);
        log.record(request, panToken, accountId, decision);
        return decision;
    }

    private AuthorizationDecision decide(AuthorizationRequest request, Optional<Card> maybeCard) {
        if (!request.isPurchase()) {
            return AuthorizationDecision.declined(ResponseCode.INVALID_TRANSACTION);
        }
        if (maybeCard.isEmpty()) {
            return AuthorizationDecision.declined(ResponseCode.INVALID_CARD_NUMBER);
        }
        Card card = maybeCard.get();
        if (card.status() != CardStatus.ACTIVE) {
            return AuthorizationDecision.declined(ResponseCode.RESTRICTED_CARD);
        }
        if (card.isExpiredOn(YearMonth.now(clock))) {
            return AuthorizationDecision.declined(ResponseCode.EXPIRED_CARD);
        }
        if (!card.expiry().equals(request.expiry())) {
            return AuthorizationDecision.declined(ResponseCode.DO_NOT_HONOR);
        }
        if (request.amountMinor() <= 0) {
            return AuthorizationDecision.declined(ResponseCode.INVALID_AMOUNT);
        }

        CreditAccount account = accounts.lockById(card.accountId())
                .orElseThrow(() -> new IllegalStateException("Card points to a missing account: " + card.accountId()));
        if (!account.currency().equals(request.currency())) {
            return AuthorizationDecision.declined(ResponseCode.TRANSACTION_NOT_PERMITTED);
        }
        if (!account.canHold(request.amountMinor())) {
            return AuthorizationDecision.declined(ResponseCode.INSUFFICIENT_FUNDS);
        }
        accounts.updateAvailable(account.id(), account.availableMinor() - request.amountMinor());
        return AuthorizationDecision.approved(newAuthCode());
    }

    private String newAuthCode() {
        return "%06d".formatted(random.nextInt(1_000_000));
    }
}
