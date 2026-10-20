package io.github.armandocalz.authorizer.authorization;

import io.github.armandocalz.authorizer.account.CreditAccount;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import io.github.armandocalz.authorizer.card.PanTokenizer;
import io.github.armandocalz.authorizer.iso8583.ResponseCode;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code 0400} reversals: undo an authorization whose outcome the terminal does not know,
 * releasing held credit exactly once (ADR 0005).
 *
 * <ul>
 *   <li>Original approved and not yet reversed: release the hold, answer 00</li>
 *   <li>Original declined, or already reversed: nothing to release, answer 00</li>
 *   <li>Original not received (yet): record the reversal, answer 00; if the original arrives
 *       later it is declined without holding credit</li>
 *   <li>Card or amount differ from the original: 12 or 13, nothing released or recorded</li>
 *   <li>Repeated reversal: 00, nothing released again</li>
 * </ul>
 */
@Service
public class ReversalService {

    private final TransactionKeyLock keyLock;
    private final ReversalLog reversals;
    private final AuthorizationLog authorizations;
    private final CreditAccountRepository accounts;
    private final PanTokenizer tokenizer;

    public ReversalService(TransactionKeyLock keyLock, ReversalLog reversals, AuthorizationLog authorizations,
            CreditAccountRepository accounts, PanTokenizer tokenizer) {
        this.keyLock = keyLock;
        this.reversals = reversals;
        this.authorizations = authorizations;
        this.accounts = accounts;
        this.tokenizer = tokenizer;
    }

    @Transactional
    public ResponseCode reverse(ReversalRequest request) {
        if (!request.reversesAnAuthorization()) {
            return ResponseCode.INVALID_TRANSACTION;
        }
        TransactionKey key = request.originalKey();
        keyLock.lock(key);
        if (reversals.existsFor(key)) {
            return ResponseCode.APPROVED;
        }

        String panToken = tokenizer.token(request.pan());
        Optional<ReversibleAuthorization> original = authorizations.findForReversal(key);
        if (original.isPresent()) {
            ReversibleAuthorization authorization = original.get();
            if (!authorization.panToken().equals(panToken)) {
                return ResponseCode.INVALID_TRANSACTION;
            }
            if (authorization.amountMinor() != request.amountMinor()) {
                return ResponseCode.INVALID_AMOUNT;
            }
            if (authorization.approved() && !authorization.reversed()) {
                CreditAccount account = accounts.lockById(authorization.accountId()).orElseThrow();
                accounts.updateAvailable(account.id(), account.availableMinor() + authorization.amountMinor());
                authorizations.markReversed(authorization.id());
            }
        }
        reversals.record(request, panToken, original.isPresent());
        return ResponseCode.APPROVED;
    }
}
