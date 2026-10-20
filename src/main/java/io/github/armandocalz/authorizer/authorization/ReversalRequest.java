package io.github.armandocalz.authorizer.authorization;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ORIGINAL_DATA_ELEMENTS;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RETRIEVAL_REFERENCE_NUMBER;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TERMINAL_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;

import io.github.armandocalz.authorizer.iso8583.IsoFormatException;
import io.github.armandocalz.authorizer.iso8583.IsoMessage;

/**
 * The fields of a {@code 0400} reversal the host needs. Field 90 (original data elements, n42)
 * starts with the original MTI (4) and the original STAN (6), then the original transmission
 * date and time (10) and the acquiring and forwarding institution ids (11 each).
 */
public record ReversalRequest(
        String pan,
        long amountMinor,
        String stan,
        String rrn,
        String terminalId,
        String transmissionDateTime,
        String originalMti,
        String originalStan) {

    /**
     * @throws IsoFormatException if a required field is missing or malformed (answered with 30)
     */
    public static ReversalRequest from(IsoMessage message) {
        String original = message.require(ORIGINAL_DATA_ELEMENTS);
        return new ReversalRequest(
                message.require(PAN),
                Long.parseLong(message.require(AMOUNT)),
                message.require(STAN),
                message.require(RETRIEVAL_REFERENCE_NUMBER),
                message.require(TERMINAL_ID),
                message.require(TRANSMISSION_DATE_TIME),
                original.substring(0, 4),
                original.substring(4, 10));
    }

    /** The key of the authorization being reversed. */
    public TransactionKey originalKey() {
        return new TransactionKey(terminalId, rrn, originalStan);
    }

    public boolean reversesAnAuthorization() {
        return originalMti.equals("0100") || originalMti.equals("0101");
    }
}
