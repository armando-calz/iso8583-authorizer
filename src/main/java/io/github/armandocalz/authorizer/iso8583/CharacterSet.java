package io.github.armandocalz.authorizer.iso8583;

import java.util.regex.Pattern;

/** ISO 8583 data element character sets, as used by this host (ADR 0001). */
public enum CharacterSet {
    /** {@code n}: digits. */
    NUMERIC("[0-9]*"),
    /** {@code an}: letters and digits. */
    ALPHANUMERIC("[A-Za-z0-9]*"),
    /** {@code ans}: printable ASCII, including space. */
    ALPHANUMERIC_SPECIAL("[\\x20-\\x7E]*"),
    /** {@code z}: track 2 data, digits plus the {@code =} or {@code D} field separator. */
    TRACK_2("[0-9=D]*"),
    /** {@code b}: binary, carried as uppercase hex (two characters per byte). */
    BINARY("([0-9A-F]{2})*");

    private final Pattern pattern;

    CharacterSet(String regex) {
        this.pattern = Pattern.compile(regex);
    }

    boolean accepts(String value) {
        return pattern.matcher(value).matches();
    }
}
