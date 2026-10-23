package io.github.armandocalz.authorizer.terminal;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.CURRENCY_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.EXPIRATION_DATE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ICC_DATA;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.NETWORK_MANAGEMENT_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ORIGINAL_DATA_ELEMENTS;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.POS_ENTRY_MODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PROCESSING_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RESPONSE_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RETRIEVAL_REFERENCE_NUMBER;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TERMINAL_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;

import io.github.armandocalz.authorizer.emv.EmvCardSimulator;
import io.github.armandocalz.authorizer.emv.EmvCryptography;
import io.github.armandocalz.authorizer.emv.IccData;
import io.github.armandocalz.authorizer.emv.Tlv;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import io.github.armandocalz.authorizer.network.IsoClient;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A point-of-sale terminal: builds ISO 8583 requests the way a terminal would, sends them over
 * one connection and plays the chip for EMV purchases (with test keys).
 */
public final class Terminal implements AutoCloseable {

    public static final String TERMINAL_ID_VALUE = "DEMO0001";
    private static final DateTimeFormatter TRANSMISSION = DateTimeFormatter.ofPattern("MMddHHmmss");
    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    private final IsoClient client;
    private final EmvCryptography cardKeys;
    private final EmvCardSimulator chip;
    private final ChipCounter atc;
    private final String currency;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger sequence = new AtomicInteger(random.nextInt(900_000));

    public Terminal(IsoClient client, EmvCryptography cardKeys, ChipCounter atc, String currency) {
        this.client = client;
        this.cardKeys = cardKeys;
        this.chip = new EmvCardSimulator(cardKeys);
        this.atc = atc;
        this.currency = currency;
    }

    /** A request and its response, with the round-trip time. */
    public record Exchange(IsoMessage request, IsoMessage response, Duration elapsed) {

        public String responseCode() {
            return response.get(RESPONSE_CODE).orElse("--");
        }
    }

    public IsoMessage.Builder echo() {
        return keys(IsoMessage.builder("0800")).set(NETWORK_MANAGEMENT_CODE, "301");
    }

    /** Magnetic stripe purchase (POS entry mode 901). */
    public IsoMessage.Builder purchase(String pan, YearMonth expiry, long amountMinor) {
        return keys(IsoMessage.builder("0100"))
                .set(PAN, pan)
                .set(PROCESSING_CODE, "000000")
                .set(AMOUNT, "%012d".formatted(amountMinor))
                .set(EXPIRATION_DATE, "%02d%02d".formatted(expiry.getYear() % 100, expiry.getMonthValue()))
                .set(POS_ENTRY_MODE, "901")
                .set(CURRENCY_CODE, currency);
    }

    /** Contact chip purchase (POS entry mode 051): field 55 carries the ARQC the chip computed. */
    public IsoMessage.Builder chipPurchase(String pan, YearMonth expiry, long amountMinor) {
        byte[] unpredictableNumber = new byte[4];
        random.nextBytes(unpredictableNumber);
        return purchase(pan, expiry, amountMinor)
                .set(POS_ENTRY_MODE, "051")
                .set(ICC_DATA, chip.iccData(pan, "01", atc.next(), amountMinor, currency, LocalDate.now(ZoneOffset.UTC), unpredictableNumber));
    }

    /** A 0400 for {@code original}: same card, amount, RRN and terminal; field 90 points back to it. */
    public IsoMessage.Builder reversalOf(IsoMessage original) {
        IsoMessage.Builder reversal = keys(IsoMessage.builder("0400"))
                .set(PAN, original.require(PAN))
                .set(PROCESSING_CODE, original.require(PROCESSING_CODE))
                .set(AMOUNT, original.require(AMOUNT))
                .set(CURRENCY_CODE, original.require(CURRENCY_CODE))
                .set(RETRIEVAL_REFERENCE_NUMBER, original.require(RETRIEVAL_REFERENCE_NUMBER))
                .set(ORIGINAL_DATA_ELEMENTS, original.mti() + original.require(STAN)
                        + original.require(TRANSMISSION_DATE_TIME) + "0".repeat(22));
        return reversal;
    }

    /** A copy of {@code request} with a new STAN and RRN: the same card data as a new transaction. */
    public IsoMessage.Builder asNewTransaction(IsoMessage request) {
        return keys(request.toBuilder(request.mti()));
    }

    public Exchange send(IsoMessage request) throws IOException {
        long start = System.nanoTime();
        IsoMessage response = client.send(request);
        return new Exchange(request, response, Duration.ofNanos(System.nanoTime() - start));
    }

    /** Whether the response carries an ARPC that proves the issuer knows this card's keys. */
    public boolean issuerAuthenticated(Exchange exchange) {
        return exchange.response().get(ICC_DATA)
                .map(hex -> Tlv.parse(HEX.parseHex(hex)).get(IccData.ISSUER_AUTHENTICATION_DATA))
                .map(received -> Arrays.equals(received, cardKeys.issuerAuthenticationData(
                        exchange.request().require(PAN),
                        IccData.parse(exchange.request().require(ICC_DATA)),
                        exchange.responseCode())))
                .orElse(false);
    }

    /** Terminal id, transmission time, and a fresh STAN and RRN. */
    private IsoMessage.Builder keys(IsoMessage.Builder message) {
        int n = sequence.incrementAndGet() % 1_000_000;
        long rrn = (System.currentTimeMillis() / 1000 % 1_000_000) * 1_000_000 + n;
        return message
                .set(TERMINAL_ID, TERMINAL_ID_VALUE)
                .set(TRANSMISSION_DATE_TIME, LocalDateTime.now(ZoneOffset.UTC).format(TRANSMISSION))
                .set(STAN, "%06d".formatted(n))
                .set(RETRIEVAL_REFERENCE_NUMBER, "%012d".formatted(rrn % 1_000_000_000_000L));
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
