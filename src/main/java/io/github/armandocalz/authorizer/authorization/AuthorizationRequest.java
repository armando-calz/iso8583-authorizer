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

import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.time.DateTimeException;
import java.time.YearMonth;

/** The fields of a {@code 0100} the authorization rules need, parsed and typed. */
public record AuthorizationRequest(
        String pan,
        String processingCode,
        long amountMinor,
        String currency,
        YearMonth expiry,
        String stan,
        String rrn,
        String terminalId,
        String transmissionDateTime) {

    /**
     * @throws IsoFormatException if a required field is missing or malformed (answered with 30)
     */
    public static AuthorizationRequest from(IsoMessage message) {
        String expiry = message.require(EXPIRATION_DATE);
        YearMonth parsedExpiry;
        try {
            parsedExpiry = YearMonth.of(2000 + Integer.parseInt(expiry.substring(0, 2)), Integer.parseInt(expiry.substring(2)));
        } catch (DateTimeException e) {
            throw new IsoFormatException("Field 14: invalid expiration date " + expiry);
        }
        return new AuthorizationRequest(
                message.require(PAN),
                message.require(PROCESSING_CODE),
                Long.parseLong(message.require(AMOUNT)),
                message.require(CURRENCY_CODE),
                parsedExpiry,
                message.require(STAN),
                message.require(RETRIEVAL_REFERENCE_NUMBER),
                message.require(TERMINAL_ID),
                message.require(TRANSMISSION_DATE_TIME));
    }

    public TransactionKey key() {
        return new TransactionKey(terminalId, rrn, stan);
    }

    /** Processing code 00xxxx: purchase of goods or services. */
    public boolean isPurchase() {
        return processingCode.startsWith("00");
    }
}
