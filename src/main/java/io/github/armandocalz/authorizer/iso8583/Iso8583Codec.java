package io.github.armandocalz.authorizer.iso8583;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Encodes and decodes ISO 8583:1987 messages in this host's ASCII dialect (ADR 0001):
 * MTI, primary bitmap and optional secondary bitmap as hex, then the data elements in order.
 * Stateless and thread-safe.
 */
public final class Iso8583Codec {

    private static final int MTI_LENGTH = 4;
    private static final int BITMAP_HEX_LENGTH = 16;

    public byte[] encode(IsoMessage message) {
        boolean secondary = !message.fields().isEmpty() && message.fields().lastKey() > 64;
        long primary = secondary ? bit(1) : 0;
        long secondaryBits = 0;
        StringBuilder data = new StringBuilder();

        for (Map.Entry<Integer, String> field : message.fields().entrySet()) {
            int number = field.getKey();
            if (number <= 64) {
                primary |= bit(number);
            } else {
                secondaryBits |= bit(number - 64);
            }
            data.append(encodeField(Iso8583Fields.require(number), field.getValue()));
        }

        StringBuilder out = new StringBuilder(message.mti()).append(hex(primary));
        if (secondary) {
            out.append(hex(secondaryBits));
        }
        return out.append(data).toString().getBytes(StandardCharsets.US_ASCII);
    }

    public IsoMessage decode(byte[] bytes) {
        Reader in = new Reader(new String(bytes, StandardCharsets.US_ASCII));
        IsoMessage.Builder message = IsoMessage.builder(in.take(MTI_LENGTH, "MTI"));

        long primary = in.bitmap("primary bitmap");
        long secondary = isSet(primary, 1) ? in.bitmap("secondary bitmap") : 0;

        for (int number = 2; number <= 128; number++) {
            boolean present = number <= 64 ? isSet(primary, number) : isSet(secondary, number - 64);
            if (present) {
                message.set(number, decodeField(Iso8583Fields.require(number), in));
            }
        }
        if (!in.atEnd()) {
            throw new IsoFormatException("%d unexpected trailing bytes".formatted(in.remaining()));
        }
        return message.build();
    }

    private static String encodeField(FieldDefinition definition, String value) {
        if (definition.lengthType() == LengthType.FIXED) {
            return value;
        }
        String length = Integer.toString(definition.unitsOf(value));
        return "0".repeat(definition.lengthType().prefixDigits() - length.length()) + length + value;
    }

    private static String decodeField(FieldDefinition definition, Reader in) {
        int number = definition.number();
        int units = definition.length();
        if (definition.lengthType() != LengthType.FIXED) {
            String prefix = in.take(definition.lengthType().prefixDigits(), "field " + number + " length");
            if (!CharacterSet.NUMERIC.accepts(prefix)) {
                throw IsoFormatException.field(number, "length prefix '%s' is not numeric".formatted(prefix));
            }
            units = Integer.parseInt(prefix);
        }
        return in.take(definition.charsFor(units), "field " + number);
    }

    /** Bit {@code n} (1-based, most significant first) of a 64-bit bitmap. */
    private static long bit(int n) {
        return 1L << (64 - n);
    }

    private static boolean isSet(long bitmap, int n) {
        return (bitmap & bit(n)) != 0;
    }

    private static String hex(long bitmap) {
        return HexFormat.of().withUpperCase().toHexDigits(bitmap);
    }

    private static final class Reader {

        private final String data;
        private int position;

        Reader(String data) {
            this.data = data;
        }

        String take(int length, String what) {
            if (position + length > data.length()) {
                throw new IsoFormatException("Message truncated while reading %s: needed %d, %d left"
                        .formatted(what, length, data.length() - position));
            }
            String value = data.substring(position, position + length);
            position += length;
            return value;
        }

        long bitmap(String what) {
            String hex = take(BITMAP_HEX_LENGTH, what);
            if (!hex.matches("[0-9A-Fa-f]{16}")) {
                throw new IsoFormatException("Invalid %s '%s'".formatted(what, hex));
            }
            return HexFormat.fromHexDigitsToLong(hex);
        }

        boolean atEnd() {
            return position == data.length();
        }

        int remaining() {
            return data.length() - position;
        }
    }
}
