package io.github.armandocalz.authorizer.iso8583;

import java.util.Random;

/** Generates random messages that are valid under the field table, for round-trip tests. */
final class RandomMessages {

    private static final String DIGITS = "0123456789";
    private static final String ALNUM = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String HEX = "0123456789ABCDEF";

    private RandomMessages() {
    }

    static IsoMessage next(Random random) {
        IsoMessage.Builder builder = IsoMessage.builder(text(random, DIGITS, 4));
        for (int number = 2; number <= 128; number++) {
            int field = number;
            Iso8583Fields.definition(field)
                    .filter(definition -> random.nextBoolean())
                    .ifPresent(definition -> builder.set(field, valueFor(definition, random)));
        }
        return builder.build();
    }

    private static String valueFor(FieldDefinition definition, Random random) {
        int units = definition.lengthType() == LengthType.FIXED ? definition.length() : 1 + random.nextInt(definition.length());
        return switch (definition.charset()) {
            case NUMERIC -> text(random, DIGITS, units);
            case ALPHANUMERIC -> text(random, ALNUM, units);
            case ALPHANUMERIC_SPECIAL -> printable(random, units);
            case TRACK_2 -> text(random, DIGITS + "=D", units);
            case BINARY -> text(random, HEX, units * 2);
        };
    }

    private static String text(Random random, String alphabet, int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return out.toString();
    }

    private static String printable(Random random, int length) {
        StringBuilder out = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            out.append((char) (0x20 + random.nextInt(0x7F - 0x20)));
        }
        return out.toString();
    }
}
