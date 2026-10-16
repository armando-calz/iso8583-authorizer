package io.github.armandocalz.authorizer.network;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Message framing on a TCP stream: a 2-byte, big-endian, unsigned length, then the message
 * (ADR 0001). TCP delivers a byte stream, not messages; this restores the message boundaries.
 */
public final class Framing {

    public static final int MAX_MESSAGE_LENGTH = 0xFFFF;

    private Framing() {
    }

    /**
     * Reads the next message.
     *
     * @return empty if the peer closed the connection cleanly between messages
     * @throws EOFException if the connection closed in the middle of a message
     */
    public static Optional<byte[]> read(InputStream in) throws IOException {
        int high = in.read();
        if (high == -1) {
            return Optional.empty();
        }
        int low = in.read();
        if (low == -1) {
            throw new EOFException("Connection closed inside a length prefix");
        }
        byte[] message = new byte[(high << 8) | low];
        new DataInputStream(in).readFully(message);
        return Optional.of(message);
    }

    public static void write(OutputStream out, byte[] message) throws IOException {
        if (message.length > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message of %d bytes exceeds the 2-byte length prefix".formatted(message.length));
        }
        byte[] frame = new byte[message.length + 2];
        frame[0] = (byte) (message.length >>> 8);
        frame[1] = (byte) message.length;
        System.arraycopy(message, 0, frame, 2, message.length);
        out.write(frame);
        out.flush();
    }
}
