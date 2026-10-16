package io.github.armandocalz.authorizer.iso8583;

import java.util.Arrays;
import java.util.Optional;

/** ISO 8583:1987 response codes (field 39) used by this host. */
public enum ResponseCode {
    APPROVED("00"),
    DO_NOT_HONOR("05"),
    INVALID_TRANSACTION("12"),
    INVALID_AMOUNT("13"),
    INVALID_CARD_NUMBER("14"),
    FORMAT_ERROR("30"),
    INSUFFICIENT_FUNDS("51"),
    EXPIRED_CARD("54"),
    TRANSACTION_NOT_PERMITTED("57"),
    RESTRICTED_CARD("62"),
    DUPLICATE_TRANSMISSION("94"),
    SYSTEM_MALFUNCTION("96");

    private final String code;

    ResponseCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean isApproval() {
        return this == APPROVED;
    }

    public static Optional<ResponseCode> fromCode(String code) {
        return Arrays.stream(values()).filter(rc -> rc.code.equals(code)).findFirst();
    }
}
