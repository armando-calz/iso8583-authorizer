package io.github.armandocalz.authorizer.host;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AUTHORIZATION_ID_RESPONSE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.CURRENCY_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.EXPIRATION_DATE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ORIGINAL_DATA_ELEMENTS;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PROCESSING_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RESPONSE_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RETRIEVAL_REFERENCE_NUMBER;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.armandocalz.authorizer.PostgresTestcontainer;
import io.github.armandocalz.authorizer.account.CreditAccount;
import io.github.armandocalz.authorizer.account.CreditAccountRepository;
import io.github.armandocalz.authorizer.authorization.AuthorizationRequests;
import io.github.armandocalz.authorizer.card.Card;
import io.github.armandocalz.authorizer.card.CardRepository;
import io.github.armandocalz.authorizer.card.CardStatus;
import io.github.armandocalz.authorizer.card.PanTokenizer;
import io.github.armandocalz.authorizer.host.IsoServerConfiguration.IsoServerLifecycle;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.IsoClient;
import java.time.Duration;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 0100 -> 0110 over TCP against the running host and PostgreSQL. */
@SpringBootTest(properties = "authorizer.iso.port=0")
@Import(PostgresTestcontainer.class)
class AuthorizationIT {

    private static final String MXN = "484";
    private static final YearMonth VALID_EXPIRY = YearMonth.now().plusYears(3);
    /** Test PANs: a fixed BIN, a counter, and a Luhn check digit, so every test gets its own card. */
    private static final AtomicLong PAN_SEQUENCE = new AtomicLong();

    @Autowired IsoServerLifecycle server;
    @Autowired CardRepository cards;
    @Autowired CreditAccountRepository accounts;
    @Autowired PanTokenizer tokenizer;
    @Autowired JdbcClient jdbc;

    private record TestCard(String pan, YearMonth expiry, UUID accountId) {
    }

    private TestCard issueCard(long creditLimitMinor, YearMonth expiry, CardStatus status) {
        CreditAccount account = CreditAccount.open(MXN, creditLimitMinor);
        accounts.create(account);
        String pan = withCheckDigit("411111" + "%09d".formatted(PAN_SEQUENCE.incrementAndGet()));
        Card card = Card.issue(pan, tokenizer, expiry, account.id());
        cards.save(new Card(card.panToken(), card.bin(), card.last4(), card.expiry(), status, card.accountId()));
        return new TestCard(pan, expiry, account.id());
    }

    private TestCard activeCard(long creditLimitMinor) {
        return issueCard(creditLimitMinor, VALID_EXPIRY, CardStatus.ACTIVE);
    }

    private static String withCheckDigit(String partial) {
        for (int d = 0; d <= 9; d++) {
            if (io.github.armandocalz.authorizer.card.Luhn.isValid(partial + d)) {
                return partial + d;
            }
        }
        throw new IllegalStateException();
    }

    private IsoMessage send(IsoMessage request) throws Exception {
        try (IsoClient client = IsoClient.connect("localhost", server.port(), Duration.ofSeconds(5))) {
            return client.send(request);
        }
    }

    private String responseCode(IsoMessage.Builder request) throws Exception {
        return send(request.build()).require(RESPONSE_CODE);
    }

    private long available(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().availableMinor();
    }

    @Test
    void approvesAndHoldsTheAmount() throws Exception {
        TestCard card = activeCard(100_000);
        IsoMessage request = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();

        IsoMessage response = send(request);

        assertThat(response.mti()).isEqualTo("0110");
        assertThat(response.require(RESPONSE_CODE)).isEqualTo("00");
        assertThat(response.require(AUTHORIZATION_ID_RESPONSE)).matches("[0-9]{6}");
        assertThat(response.require(STAN)).isEqualTo(request.require(STAN));
        assertThat(response.require(RETRIEVAL_REFERENCE_NUMBER)).isEqualTo(request.require(RETRIEVAL_REFERENCE_NUMBER));
        assertThat(available(card.accountId())).isEqualTo(75_000);
    }

    @Nested
    class Declines {

        @Test
        void insufficientCreditIs51AndHoldsNothing() throws Exception {
            TestCard card = activeCard(10_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_001))).isEqualTo("51");
            assertThat(available(card.accountId())).isEqualTo(10_000);
            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000))).isEqualTo("00");
        }

        @Test
        void unknownCardOrFailedLuhnCheckIs14() throws Exception {
            assertThat(responseCode(AuthorizationRequests.purchase(withCheckDigit("400000999999999"), VALID_EXPIRY, 100)))
                    .isEqualTo("14");
            assertThat(responseCode(AuthorizationRequests.purchase("4111111111111112", VALID_EXPIRY, 100))).isEqualTo("14");
        }

        @Test
        void blockedLostOrStolenCardsAre62() throws Exception {
            for (CardStatus status : List.of(CardStatus.BLOCKED, CardStatus.LOST, CardStatus.STOLEN)) {
                TestCard card = issueCard(100_000, VALID_EXPIRY, status);
                assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 100))).as(status.name())
                        .isEqualTo("62");
            }
        }

        @Test
        void expiredCardIs54() throws Exception {
            TestCard card = issueCard(100_000, YearMonth.now().minusMonths(1), CardStatus.ACTIVE);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 100))).isEqualTo("54");
        }

        @Test
        void wrongExpiryDateIs05() throws Exception {
            TestCard card = activeCard(100_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry().plusMonths(1), 100)))
                    .isEqualTo("05");
        }

        @Test
        void zeroAmountIs13() throws Exception {
            TestCard card = activeCard(100_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 0))).isEqualTo("13");
        }

        @Test
        void otherCurrencyIs57() throws Exception {
            TestCard card = activeCard(100_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 100).set(CURRENCY_CODE, "840")))
                    .isEqualTo("57");
        }

        @Test
        void nonPurchaseProcessingCodeIs12() throws Exception {
            TestCard card = activeCard(100_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 100).set(PROCESSING_CODE, "200000")))
                    .isEqualTo("12");
        }

        @Test
        void missingRequiredFieldIs30() throws Exception {
            TestCard card = activeCard(100_000);

            assertThat(responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 100).remove(EXPIRATION_DATE)))
                    .isEqualTo("30");
        }
    }

    @Nested
    class Retransmissions {

        private long recordedRows(UUID accountId) {
            return jdbc.sql("SELECT count(*) FROM authorizations WHERE account_id = :id").param("id", accountId)
                    .query(Long.class).single();
        }

        @Test
        void sameRequestGetsTheOriginalResponseAndHoldsOnce() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage request = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();

            IsoMessage original = send(request);
            // A retransmission may carry a new transmission date/time (field 7)
            IsoMessage retransmitted = send(request.toBuilder("0100").set(TRANSMISSION_DATE_TIME, "1018093000").build());

            assertThat(retransmitted.require(RESPONSE_CODE)).isEqualTo("00");
            assertThat(retransmitted.require(AUTHORIZATION_ID_RESPONSE)).isEqualTo(original.require(AUTHORIZATION_ID_RESPONSE));
            assertThat(available(card.accountId())).isEqualTo(75_000);
            assertThat(recordedRows(card.accountId())).isEqualTo(1);
        }

        @Test
        void repeatMtiIsAnsweredLikeTheOriginal() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage request = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            IsoMessage original = send(request);

            IsoMessage repeat = send(request.toBuilder("0101").build());

            assertThat(repeat.mti()).isEqualTo("0110");
            assertThat(repeat.require(AUTHORIZATION_ID_RESPONSE)).isEqualTo(original.require(AUTHORIZATION_ID_RESPONSE));
            assertThat(available(card.accountId())).isEqualTo(75_000);
        }

        @Test
        void sameKeyWithDifferentDataIs94() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage request = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            send(request);

            assertThat(send(request.toBuilder("0100").set(AMOUNT, "%012d".formatted(30_000)).build()).require(RESPONSE_CODE))
                    .isEqualTo("94");
            assertThat(available(card.accountId())).isEqualTo(75_000);
        }

        @Test
        void aDeclineIsRepeatedNotReevaluated() throws Exception {
            TestCard card = activeCard(10_000);
            IsoMessage tooMuch = AuthorizationRequests.purchase(card.pan(), card.expiry(), 20_000).build();
            assertThat(send(tooMuch).require(RESPONSE_CODE)).isEqualTo("51");

            assertThat(send(tooMuch).require(RESPONSE_CODE)).isEqualTo("51");
            assertThat(recordedRows(card.accountId())).isEqualTo(1);
        }

        @Test
        void aNewStanIsANewTransaction() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage first = AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000).build();
            send(first);

            IsoMessage second = first.toBuilder("0100").set(STAN, "999998").build();
            assertThat(send(second).require(RESPONSE_CODE)).isEqualTo("00");
            assertThat(available(card.accountId())).isEqualTo(80_000);
        }

        @Test
        void simultaneousCopiesAreAuthorizedOnce() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage request = AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000).build();

            List<Future<IsoMessage>> responses = new ArrayList<>();
            try (ExecutorService terminals = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 20; i++) {
                    responses.add(terminals.submit(() -> send(request)));
                }
                List<IsoMessage> results = new ArrayList<>();
                for (Future<IsoMessage> response : responses) {
                    results.add(response.get());
                }
                assertThat(results).extracting(r -> r.require(RESPONSE_CODE)).containsOnly("00");
                assertThat(results).extracting(r -> r.require(AUTHORIZATION_ID_RESPONSE))
                        .containsOnly(results.getFirst().require(AUTHORIZATION_ID_RESPONSE));
            }
            assertThat(available(card.accountId())).isEqualTo(90_000);
            assertThat(recordedRows(card.accountId())).isEqualTo(1);
        }
    }

    @Nested
    class Reversals {

        private String reverse(IsoMessage.Builder reversal) throws Exception {
            IsoMessage response = send(reversal.build());
            assertThat(response.mti()).isEqualTo("0410");
            return response.require(RESPONSE_CODE);
        }

        @Test
        void releasesTheHoldOfAnApprovedAuthorization() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            assertThat(send(purchase).require(RESPONSE_CODE)).isEqualTo("00");

            assertThat(reverse(AuthorizationRequests.reversalOf(purchase))).isEqualTo("00");

            assertThat(available(card.accountId())).isEqualTo(100_000);
            assertThat(jdbc.sql("SELECT reversed_at IS NOT NULL FROM authorizations WHERE account_id = :id")
                    .param("id", card.accountId()).query(Boolean.class).single()).isTrue();
        }

        @Test
        void repeatedReversalsReleaseOnlyOnce() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            send(purchase);
            send(AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000).build()); // another, still held
            IsoMessage reversal = AuthorizationRequests.reversalOf(purchase).build();

            assertThat(reverse(reversal.toBuilder("0400"))).isEqualTo("00");
            assertThat(reverse(reversal.toBuilder("0400"))).isEqualTo("00");
            assertThat(reverse(reversal.toBuilder("0401"))).isEqualTo("00");

            assertThat(available(card.accountId())).isEqualTo(90_000);
        }

        @Test
        void reversingADeclineReleasesNothing() throws Exception {
            TestCard card = activeCard(10_000);
            send(AuthorizationRequests.purchase(card.pan(), card.expiry(), 4_000).build());
            IsoMessage declined = AuthorizationRequests.purchase(card.pan(), card.expiry(), 20_000).build();
            assertThat(send(declined).require(RESPONSE_CODE)).isEqualTo("51");

            assertThat(reverse(AuthorizationRequests.reversalOf(declined))).isEqualTo("00");
            assertThat(available(card.accountId())).isEqualTo(6_000);
        }

        @Test
        void anOriginalArrivingAfterItsReversalIsDeclinedWithoutHolding() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();

            assertThat(reverse(AuthorizationRequests.reversalOf(purchase))).isEqualTo("00");
            assertThat(send(purchase).require(RESPONSE_CODE)).isEqualTo("05");

            assertThat(available(card.accountId())).isEqualTo(100_000);
        }

        @Test
        void aReversalThatDoesNotMatchTheOriginalReleasesNothing() throws Exception {
            TestCard card = activeCard(100_000);
            TestCard otherCard = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            send(purchase);

            assertThat(reverse(AuthorizationRequests.reversalOf(purchase).set(AMOUNT, "%012d".formatted(20_000))))
                    .isEqualTo("13");
            assertThat(reverse(AuthorizationRequests.reversalOf(purchase).set(PAN, otherCard.pan())))
                    .isEqualTo("12");
            assertThat(available(card.accountId())).isEqualTo(75_000);

            assertThat(reverse(AuthorizationRequests.reversalOf(purchase))).as("a correct one still works").isEqualTo("00");
            assertThat(available(card.accountId())).isEqualTo(100_000);
        }

        @Test
        void malformedOrUnsupportedReversalsAreRejected() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();

            assertThat(reverse(AuthorizationRequests.reversalOf(purchase).remove(ORIGINAL_DATA_ELEMENTS))).isEqualTo("30");
            String reversesAFinancialMessage = "0200" + purchase.require(STAN) + "1016120000" + "0".repeat(22);
            assertThat(reverse(AuthorizationRequests.reversalOf(purchase).set(ORIGINAL_DATA_ELEMENTS, reversesAFinancialMessage)))
                    .isEqualTo("12");
        }

        @Test
        void simultaneousCopiesOfAReversalReleaseOnce() throws Exception {
            TestCard card = activeCard(100_000);
            IsoMessage purchase = AuthorizationRequests.purchase(card.pan(), card.expiry(), 25_000).build();
            send(purchase);
            IsoMessage reversal = AuthorizationRequests.reversalOf(purchase).build();

            List<Future<String>> codes = new ArrayList<>();
            try (ExecutorService terminals = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int i = 0; i < 20; i++) {
                    codes.add(terminals.submit(() -> reverse(reversal.toBuilder("0400"))));
                }
                for (Future<String> code : codes) {
                    assertThat(code.get()).isEqualTo("00");
                }
            }
            assertThat(available(card.accountId())).isEqualTo(100_000);
        }

        @Test
        void aPurchaseRacingItsReversalNeverLeavesCreditHeld() throws Exception {
            TestCard card = activeCard(10_000_000);
            List<IsoMessage> purchases = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                purchases.add(AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000).build());
            }

            List<Future<String>> results = new ArrayList<>();
            try (ExecutorService terminals = Executors.newVirtualThreadPerTaskExecutor()) {
                for (IsoMessage purchase : purchases) {
                    IsoMessage reversal = AuthorizationRequests.reversalOf(purchase).build();
                    results.add(terminals.submit(() -> send(purchase).require(RESPONSE_CODE)));
                    results.add(terminals.submit(() -> reverse(reversal.toBuilder("0400"))));
                }
                for (Future<String> result : results) {
                    assertThat(result.get()).isIn("00", "05");
                }
            }

            assertThat(available(card.accountId())).as("every hold released").isEqualTo(10_000_000);
            assertThat(jdbc.sql("""
                    SELECT count(*) FROM authorizations
                    WHERE account_id = :id AND response_code = '00' AND reversed_at IS NULL
                    """).param("id", card.accountId()).query(Long.class).single())
                    .as("approved but never reversed").isZero();
        }
    }

    @Test
    void concurrentAuthorizationsNeverExceedTheCreditLimit() throws Exception {
        TestCard card = activeCard(100_000); // room for exactly 10 authorizations of 100.00

        List<Future<String>> codes = new ArrayList<>();
        try (ExecutorService terminals = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 50; i++) {
                codes.add(terminals.submit(() -> responseCode(AuthorizationRequests.purchase(card.pan(), card.expiry(), 10_000))));
            }
            List<String> results = new ArrayList<>();
            for (Future<String> code : codes) {
                results.add(code.get());
            }
            assertThat(results).filteredOn("00"::equals).hasSize(10);
            assertThat(results).filteredOn("51"::equals).hasSize(40);
        }
        assertThat(available(card.accountId())).isZero();
    }

    @Test
    void recordsEveryDecisionWithoutStoringThePan() throws Exception {
        TestCard card = activeCard(5_000);
        send(AuthorizationRequests.purchase(card.pan(), card.expiry(), 5_000).build());
        send(AuthorizationRequests.purchase(card.pan(), card.expiry(), 1).build());

        List<String> recorded = jdbc.sql("SELECT response_code FROM authorizations WHERE account_id = :id ORDER BY id")
                .param("id", card.accountId()).query(String.class).list();
        assertThat(recorded).containsExactly("00", "51");

        String everything = jdbc.sql("SELECT string_agg(c::text, '|') FROM cards c").query(String.class).single()
                + jdbc.sql("SELECT string_agg(a::text, '|') FROM authorizations a").query(String.class).single();
        assertThat(everything).doesNotContain(card.pan()).contains(card.pan().substring(card.pan().length() - 4));
    }
}
