package io.github.armandocalz.authorizer.demo;

import io.github.armandocalz.authorizer.account.CreditAccount;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import io.github.armandocalz.authorizer.card.Card;
import io.github.armandocalz.authorizer.card.CardRepository;
import io.github.armandocalz.authorizer.card.CardStatus;
import io.github.armandocalz.authorizer.card.PanTokenizer;
import java.time.YearMonth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Loads the demo cards ({@link DemoCards}) on startup with the {@code demo} profile. Idempotent. */
@Component
@Profile("demo")
class DemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoData.class);

    private final CardRepository cards;
    private final CreditAccountRepository accounts;
    private final PanTokenizer tokenizer;

    DemoData(CardRepository cards, CreditAccountRepository accounts, PanTokenizer tokenizer) {
        this.cards = cards;
        this.accounts = accounts;
        this.tokenizer = tokenizer;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        load(DemoCards.ACTIVE_PAN, DemoCards.ACTIVE_EXPIRY, CardStatus.ACTIVE);
        load(DemoCards.BLOCKED_PAN, DemoCards.BLOCKED_EXPIRY, CardStatus.BLOCKED);
        load(DemoCards.EXPIRED_PAN, DemoCards.EXPIRED_EXPIRY, CardStatus.ACTIVE);
    }

    private void load(String pan, YearMonth expiry, CardStatus status) {
        if (cards.findByToken(tokenizer.token(pan)).isPresent()) {
            return;
        }
        CreditAccount account = CreditAccount.open(DemoCards.CURRENCY, DemoCards.CREDIT_LIMIT_MINOR);
        accounts.create(account);
        Card card = Card.issue(pan, tokenizer, expiry, account.id());
        cards.save(new Card(card.panToken(), card.bin(), card.last4(), card.expiry(), status, card.accountId()));
        log.info("Demo card {} loaded ({}, expires {})", card.bin() + "******" + card.last4(), status, expiry);
    }
}
