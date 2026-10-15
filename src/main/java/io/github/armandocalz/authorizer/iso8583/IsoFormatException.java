package io.github.armandocalz.authorizer.iso8583;

/** A message that cannot be encoded or decoded under this host's ISO 8583 dialect. */
public class IsoFormatException extends RuntimeException {

    public IsoFormatException(String message) {
        super(message);
    }

    static IsoFormatException field(int number, String problem) {
        return new IsoFormatException("Field %d: %s".formatted(number, problem));
    }
}
