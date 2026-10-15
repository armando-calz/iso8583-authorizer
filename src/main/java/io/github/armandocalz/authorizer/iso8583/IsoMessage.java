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

    @Override
    public String toString() {
        return "IsoMessage[" + mti + " " + fields + "]";
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
