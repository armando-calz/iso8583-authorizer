package io.github.armandocalz.authorizer.iso8583;

import static io.github.armandocalz.authorizer.iso8583.CharacterSet.ALPHANUMERIC;
import static io.github.armandocalz.authorizer.iso8583.CharacterSet.ALPHANUMERIC_SPECIAL;
import static io.github.armandocalz.authorizer.iso8583.CharacterSet.BINARY;
import static io.github.armandocalz.authorizer.iso8583.CharacterSet.NUMERIC;
import static io.github.armandocalz.authorizer.iso8583.CharacterSet.TRACK_2;
import static io.github.armandocalz.authorizer.iso8583.LengthType.FIXED;
import static io.github.armandocalz.authorizer.iso8583.LengthType.LLLVAR;
import static io.github.armandocalz.authorizer.iso8583.LengthType.LLVAR;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** The ISO 8583:1987 data elements this host understands (ADR 0001). */
public final class Iso8583Fields {

    public static final int PAN = 2;
    public static final int PROCESSING_CODE = 3;
    public static final int AMOUNT = 4;
    public static final int TRANSMISSION_DATE_TIME = 7;
    public static final int STAN = 11;
    public static final int LOCAL_TIME = 12;
    public static final int LOCAL_DATE = 13;
    public static final int EXPIRATION_DATE = 14;
    public static final int MERCHANT_CATEGORY_CODE = 18;
    public static final int POS_ENTRY_MODE = 22;
    public static final int POS_CONDITION_CODE = 25;
    public static final int ACQUIRING_INSTITUTION_ID = 32;
    public static final int TRACK_2_DATA = 35;
    public static final int RETRIEVAL_REFERENCE_NUMBER = 37;
    public static final int AUTHORIZATION_ID_RESPONSE = 38;
    public static final int RESPONSE_CODE = 39;
    public static final int TERMINAL_ID = 41;
    public static final int MERCHANT_ID = 42;
    public static final int MERCHANT_NAME_LOCATION = 43;
    public static final int CURRENCY_CODE = 49;
    public static final int ICC_DATA = 55;
    public static final int NETWORK_MANAGEMENT_CODE = 70;
    public static final int ORIGINAL_DATA_ELEMENTS = 90;

    private static final Map<Integer, FieldDefinition> DEFINITIONS = new TreeMap<>();

    static {
        define(PAN, "Primary account number", NUMERIC, LLVAR, 19);
        define(PROCESSING_CODE, "Processing code", NUMERIC, FIXED, 6);
        define(AMOUNT, "Amount, transaction", NUMERIC, FIXED, 12);
        define(TRANSMISSION_DATE_TIME, "Transmission date and time", NUMERIC, FIXED, 10);
        define(STAN, "System trace audit number", NUMERIC, FIXED, 6);
        define(LOCAL_TIME, "Local transaction time", NUMERIC, FIXED, 6);
        define(LOCAL_DATE, "Local transaction date", NUMERIC, FIXED, 4);
        define(EXPIRATION_DATE, "Expiration date", NUMERIC, FIXED, 4);
        define(MERCHANT_CATEGORY_CODE, "Merchant category code", NUMERIC, FIXED, 4);
        define(POS_ENTRY_MODE, "POS entry mode", NUMERIC, FIXED, 3);
        define(POS_CONDITION_CODE, "POS condition code", NUMERIC, FIXED, 2);
        define(ACQUIRING_INSTITUTION_ID, "Acquiring institution id", NUMERIC, LLVAR, 11);
        define(TRACK_2_DATA, "Track 2 data", TRACK_2, LLVAR, 37);
        define(RETRIEVAL_REFERENCE_NUMBER, "Retrieval reference number", ALPHANUMERIC, FIXED, 12);
        define(AUTHORIZATION_ID_RESPONSE, "Authorization id response", ALPHANUMERIC, FIXED, 6);
        define(RESPONSE_CODE, "Response code", ALPHANUMERIC, FIXED, 2);
        define(TERMINAL_ID, "Card acceptor terminal id", ALPHANUMERIC_SPECIAL, FIXED, 8);
        define(MERCHANT_ID, "Card acceptor id code", ALPHANUMERIC_SPECIAL, FIXED, 15);
        define(MERCHANT_NAME_LOCATION, "Card acceptor name/location", ALPHANUMERIC_SPECIAL, FIXED, 40);
        define(CURRENCY_CODE, "Currency code, transaction", NUMERIC, FIXED, 3);
        define(ICC_DATA, "ICC data (EMV)", BINARY, LLLVAR, 255);
        define(NETWORK_MANAGEMENT_CODE, "Network management information code", NUMERIC, FIXED, 3);
        define(ORIGINAL_DATA_ELEMENTS, "Original data elements", NUMERIC, FIXED, 42);
    }

    private Iso8583Fields() {
    }

    private static void define(int number, String name, CharacterSet charset, LengthType lengthType, int length) {
        DEFINITIONS.put(number, new FieldDefinition(number, name, charset, lengthType, length));
    }

    public static Optional<FieldDefinition> definition(int number) {
        return Optional.ofNullable(DEFINITIONS.get(number));
    }

    static FieldDefinition require(int number) {
        return definition(number).orElseThrow(() -> IsoFormatException.field(number, "not supported by this host"));
    }
}
