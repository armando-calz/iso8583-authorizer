package io.github.armandocalz.authorizer.emv;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal BER-TLV, as EMV uses it in field 55: one- or two-byte tags, short or long-form
 * lengths (up to 0x82 two-byte lengths). Primitive data objects only.
 */
public final class Tlv {

    private Tlv() {
    }

    /**
     * @throws IllegalArgumentException if the data is truncated, malformed or repeats a tag
     */
    public static Map<Integer, byte[]> parse(byte[] data) {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        int i = 0;
        while (i < data.length) {
            int tag = data[i++] & 0xFF;
            if ((tag & 0x1F) == 0x1F) {
                require(i < data.length, "truncated tag");
                tag = (tag << 8) | (data[i++] & 0xFF);
            }
            require(i < data.length, "missing length for tag " + Integer.toHexString(tag));
            int length = data[i++] & 0xFF;
            if (length == 0x81 || length == 0x82) {
                int bytes = length & 0x7F;
                require(i + bytes <= data.length, "truncated length for tag " + Integer.toHexString(tag));
                length = 0;
                for (int b = 0; b < bytes; b++) {
                    length = (length << 8) | (data[i++] & 0xFF);
                }
            } else {
                require(length < 0x80, "unsupported length form for tag " + Integer.toHexString(tag));
            }
            require(i + length <= data.length, "value of tag %X truncated".formatted(tag));
            byte[] value = new byte[length];
            System.arraycopy(data, i, value, 0, length);
            i += length;
            require(out.put(tag, value) == null, "tag %X repeated".formatted(tag));
        }
        return Collections.unmodifiableMap(out);
    }

    public static byte[] encode(Map<Integer, byte[]> objects) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        objects.forEach((tag, value) -> {
            if (tag > 0xFF) {
                out.write(tag >>> 8);
            }
            out.write(tag & 0xFF);
            if (value.length < 0x80) {
                out.write(value.length);
            } else if (value.length <= 0xFF) {
                out.write(0x81);
                out.write(value.length);
            } else {
                out.write(0x82);
                out.write(value.length >>> 8);
                out.write(value.length & 0xFF);
            }
            out.writeBytes(value);
        });
        return out.toByteArray();
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException("Malformed TLV: " + problem);
        }
    }
}
