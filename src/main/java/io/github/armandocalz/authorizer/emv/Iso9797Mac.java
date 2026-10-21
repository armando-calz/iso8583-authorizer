package io.github.armandocalz.authorizer.emv;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * ISO/IEC 9797-1 MAC Algorithm 3 ("retail MAC") with DES and padding method 2, as EMV Book 2
 * uses for application cryptograms: CBC-MAC with the left key, then the last block is decrypted
 * with the right key and encrypted again with the left key.
 */
final class Iso9797Mac {

    private Iso9797Mac() {
    }

    static byte[] algorithm3(byte[] key16, byte[] data) {
        byte[] keyLeft = Des.left(key16);
        byte[] padded = padMethod2(data);
        byte[] chain = new byte[Des.BLOCK];
        for (int offset = 0; offset < padded.length; offset += Des.BLOCK) {
            byte[] block = Arrays.copyOfRange(padded, offset, offset + Des.BLOCK);
            chain = Des.encrypt(keyLeft, Des.xor(chain, block));
        }
        return Des.encrypt(keyLeft, Des.decrypt(Des.right(key16), chain));
    }

    /** Padding method 2: append 0x80, then zeros up to a multiple of 8 bytes (always adds at least one byte). */
    static byte[] padMethod2(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(data);
        out.write(0x80);
        while (out.size() % Des.BLOCK != 0) {
            out.write(0x00);
        }
        return out.toByteArray();
    }
}
