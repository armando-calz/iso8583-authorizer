package io.github.armandocalz.authorizer.iso8583;

/**
 * Format of one data element.
 *
 * @param length exact length for {@link LengthType#FIXED} fields, maximum otherwise; counted in
 *               characters, or in bytes for {@link CharacterSet#BINARY} fields
 */
public record FieldDefinition(int number, String name, CharacterSet charset, LengthType lengthType, int length) {

    public FieldDefinition {
        if (number < 2 || number > 128) {
            throw new IllegalArgumentException("Field number must be 2..128, got " + number);
        }
        int maxLength = switch (lengthType) {
            case FIXED -> Integer.MAX_VALUE;
            case LLVAR -> 99;
            case LLLVAR -> 999;
        };
        if (length < 1 || length > maxLength) {
            throw new IllegalArgumentException("Invalid length %d for %s field %d".formatted(length, lengthType, number));
        }
    }

    /** Length of {@code value} in this field's units (bytes for binary fields). */
    int unitsOf(String value) {
        return charset == CharacterSet.BINARY ? value.length() / 2 : value.length();
    }

    /** Characters on the wire for {@code units} units of this field. */
    int charsFor(int units) {
        return charset == CharacterSet.BINARY ? units * 2 : units;
    }

    void validate(String value) {
        if (!charset.accepts(value)) {
            throw IsoFormatException.field(number, "'%s' is not valid %s data".formatted(value, charset));
        }
        int units = unitsOf(value);
        if (lengthType == LengthType.FIXED && units != length) {
            throw IsoFormatException.field(number, "expected exactly %d, got %d".formatted(length, units));
        }
        if (lengthType != LengthType.FIXED && (units < 1 || units > length)) {
            throw IsoFormatException.field(number, "expected 1..%d, got %d".formatted(length, units));
        }
    }
}
