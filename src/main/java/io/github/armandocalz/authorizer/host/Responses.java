package io.github.armandocalz.authorizer.host;

import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.ACQUIRING_INSTITUTION_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.AMOUNT;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.CURRENCY_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.LOCAL_DATE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.LOCAL_TIME;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.MERCHANT_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.NETWORK_MANAGEMENT_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.PROCESSING_CODE;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.RETRIEVAL_REFERENCE_NUMBER;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.STAN;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TERMINAL_ID;
import static io.github.armandocalz.authorizer.iso8583.Iso8583Fields.TRANSMISSION_DATE_TIME;

import io.github.armandocalz.authorizer.iso8583.IsoMessage;
import java.util.List;

/** Builds responses that carry the request's matching keys back to the sender. */
final class Responses {

    /** Fields a sender uses to match a response to its request. */
    private static final List<Integer> MATCHING_FIELDS = List.of(
            PROCESSING_CODE, AMOUNT, TRANSMISSION_DATE_TIME, STAN, LOCAL_TIME, LOCAL_DATE,
            ACQUIRING_INSTITUTION_ID, RETRIEVAL_REFERENCE_NUMBER, TERMINAL_ID, MERCHANT_ID,
            CURRENCY_CODE, NETWORK_MANAGEMENT_CODE);

    private Responses() {
    }

    static IsoMessage.Builder echoKeys(IsoMessage request, String responseMti) {
        IsoMessage.Builder response = IsoMessage.builder(responseMti);
        for (int field : MATCHING_FIELDS) {
            request.get(field).ifPresent(value -> response.set(field, value));
        }
        return response;
    }
}
