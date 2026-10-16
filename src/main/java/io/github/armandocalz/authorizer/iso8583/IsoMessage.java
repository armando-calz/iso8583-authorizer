package io.github.armandocalz.authorizer.iso8583;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * An immutable ISO 8583 message: a message type indicator (MTI) and data elements 2..128.
 * Every value is validated against its field definition when the message is built.
 * The secondary bitmap (field 1) is derived by the codec, never set directly.
 */
public final class IsoMessage {

    private static final Pattern MTI = Pattern.compile("[0-9]{4}");

    private final String mti;
    private final SortedMap<Integer, String> fields;

    private IsoMessage(String mti, SortedMap<Integer, String> fields) {
        this.mti = mti;
        this.fields = Collections.unmodifiableSortedMap(new TreeMap<>(fields));
    }

    public static Builder builder(String mti) {
        return new Builder(mti);
    }

    public String mti() {
        return mti;
    }

    /** Field numbers present, ascending. */
    public SortedMap<Integer, String> fields() {
        return fields;
    }

    public boolean has(int field) {
        return fields.containsKey(field);
    }

    public Optional<String> get(int field) {
        return Optional.ofNullable(fields.get(field));
    }

    public String require(int field) {
        String value = fields.get(field);
        if (value == null) {
            throw IsoFormatException.field(field, "required but missing in " + mti);
        }
        return value;
    }

    /** A builder for a reply that starts with this message's fields, under {@code replyMti}. */
    public Builder toBuilder(String replyMti) {
        Builder builder = new Builder(replyMti);
        builder.fields.putAll(fields);
        return builder;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof IsoMessage other && mti.equals(other.mti) && fields.equals(other.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mti, fields);
    }

    /** Safe for logs: the PAN is masked and track 2 and ICC data are never printed. */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("IsoMessage[").append(mti);
        fields.forEach((field, value) -> out.append(' ').append(field).append('=').append(masked(field, value)));
        return out.append(']').toString();
    }

    private static String masked(int field, String value) {
        return switch (field) {
            case Iso8583Fields.PAN -> maskPan(value);
            case Iso8583Fields.TRACK_2_DATA, Iso8583Fields.ICC_DATA -> "<redacted>";
            default -> value;
        };
    }

    /** First 6 and last 4 digits, the most PCI DSS allows to display. */
    public static String maskPan(String pan) {
        if (pan.length() <= 10) {
            return "*".repeat(pan.length());
        }
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }

    public static final class Builder {

        private final String mti;
        private final SortedMap<Integer, String> fields = new TreeMap<>();

        private Builder(String mti) {
            Objects.requireNonNull(mti, "mti");
            if (!MTI.matcher(mti).matches()) {
                throw new IsoFormatException("MTI must be 4 digits, got '" + mti + "'");
            }
            this.mti = mti;
        }

        public Builder set(int field, String value) {
            Objects.requireNonNull(value, "value");
            if (field == 1) {
                throw IsoFormatException.field(1, "the secondary bitmap is derived by the codec, not set");
            }
            Iso8583Fields.require(field).validate(value);
            fields.put(field, value);
            return this;
        }

        public Builder remove(int field) {
            fields.remove(field);
            return this;
        }

        public IsoMessage build() {
            return new IsoMessage(mti, fields);
        }
    }
}
