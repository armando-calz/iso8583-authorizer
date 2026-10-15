package io.github.armandocalz.authorizer.iso8583;

/** How a data element's length is determined. */
public enum LengthType {
    /** Always exactly the defined length. */
    FIXED(0),
    /** Preceded by a 2-digit length, up to 99. */
    LLVAR(2),
    /** Preceded by a 3-digit length, up to 999. */
    LLLVAR(3);

    private final int prefixDigits;

    LengthType(int prefixDigits) {
        this.prefixDigits = prefixDigits;
    }

    int prefixDigits() {
        return prefixDigits;
    }
}
