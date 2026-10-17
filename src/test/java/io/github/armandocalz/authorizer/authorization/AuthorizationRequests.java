package io.github.armandocalz.authorizer.authorization;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.CURRENCY_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.EXPIRATION_DATE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PROCESSING_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RETRIEVAL_REFERENCE_NUMBER;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TERMINAL_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;

import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.time.YearMonth;
import java.util.concurrent.atomic.AtomicInteger;

/** Builds valid 0100 requests for tests. */
public final class AuthorizationRequests {

    private static final AtomicInteger STAN_SEQUENCE = new AtomicInteger();

    private AuthorizationRequests() {
    }

    public static String yymm(YearMonth month) {
        return "%02d%02d".formatted(month.getYear() % 100, month.getMonthValue());
    }

    public static IsoMessage.Builder purchase(String pan, YearMonth expiry, long amountMinor) {
        int n = STAN_SEQUENCE.incrementAndGet() % 1_000_000;
        return IsoMessage.builder("0100")
                .set(PAN, pan)
                .set(PROCESSING_CODE, "000000")
                .set(AMOUNT, "%012d".formatted(amountMinor))
                .set(TRANSMISSION_DATE_TIME, "1016120000")
                .set(STAN, "%06d".formatted(n))
                .set(EXPIRATION_DATE, yymm(expiry))
                .set(RETRIEVAL_REFERENCE_NUMBER, "%012d".formatted(n))
                .set(TERMINAL_ID, "TERM0001")
                .set(CURRENCY_CODE, "484");
    }
}
