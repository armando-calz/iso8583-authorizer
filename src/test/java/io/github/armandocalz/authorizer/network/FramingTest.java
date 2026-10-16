package io.github.armandocalz.authorizer.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FramingTest {

    private static byte[] framed(byte[]... messages) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] message : messages) {
            Framing.write(out, message);
        }
        return out.toByteArray();
    }

    @Test
    void prefixesTheLengthAsTwoBigEndianBytes() throws Exception {
        assertThat(framed("0800".getBytes(StandardCharsets.US_ASCII))).containsExactly(0, 4, '0', '8', '0', '0');
        assertThat(framed(new byte[300])).startsWith(1, 44); // 300 = 0x012C
    }

    @Test
    void restoresMessageBoundariesFromAStream() throws Exception {
        byte[] first = "first".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "the second one".getBytes(StandardCharsets.US_ASCII);
        ByteArrayInputStream in = new ByteArrayInputStream(framed(first, new byte[0], second));

        assertThat(Framing.read(in)).hasValue(first);
        assertThat(Framing.read(in)).hasValue(new byte[0]);
        assertThat(Framing.read(in)).hasValue(second);
        assertThat(Framing.read(in)).as("clean end of stream").isEmpty();
    }

    @Test
    void supportsTheMaximumLengthAndRejectsLonger() throws Exception {
        byte[] max = new byte[Framing.MAX_MESSAGE_LENGTH];

        assertThat(Framing.read(new ByteArrayInputStream(framed(max)))).hasValueSatisfying(m -> assertThat(m).hasSize(65_535));
        assertThatThrownBy(() -> framed(new byte[Framing.MAX_MESSAGE_LENGTH + 1])).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsAConnectionClosedMidMessage() {
        assertThatThrownBy(() -> Framing.read(new ByteArrayInputStream(new byte[] {0})))
                .isInstanceOf(EOFException.class);
        assertThatThrownBy(() -> Framing.read(new ByteArrayInputStream(new byte[] {0, 10, '0', '8'})))
                .isInstanceOf(EOFException.class);
    }
}
