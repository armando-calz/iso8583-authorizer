package io.github.armandocalz.authorizer.emv;

import java.security.GeneralSecurityException;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/** Single-block DES and two-key triple DES (EDE, K1 K2 K1) on 8-byte blocks, via the JCE. */
final class Des {

    static final int BLOCK = 8;

    private Des() {
    }

    static byte[] encrypt(byte[] key8, byte[] block) {
        return run(Cipher.ENCRYPT_MODE, "DES", key8, block);
    }

    static byte[] decrypt(byte[] key8, byte[] block) {
        return run(Cipher.DECRYPT_MODE, "DES", key8, block);
    }

    /** Two-key triple DES: encrypt with K1, decrypt with K2, encrypt with K1. */
    static byte[] tripleEncrypt(byte[] key16, byte[] block) {
        return run(Cipher.ENCRYPT_MODE, "DESede", expand(key16), block);
    }

    static byte[] left(byte[] key16) {
        return java.util.Arrays.copyOfRange(key16, 0, 8);
    }

    static byte[] right(byte[] key16) {
        return java.util.Arrays.copyOfRange(key16, 8, 16);
    }

    static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }

    /** Sets the least significant bit of each byte so every byte has odd parity, as DES keys require. */
    static byte[] withOddParity(byte[] key) {
        byte[] out = key.clone();
        for (int i = 0; i < out.length; i++) {
            int b = out[i] & 0xFE;
            out[i] = (byte) (Integer.bitCount(b) % 2 == 0 ? b | 1 : b);
        }
        return out;
    }

    private static byte[] expand(byte[] key16) {
        if (key16.length != 16) {
            throw new IllegalArgumentException("Double-length key must be 16 bytes, got " + key16.length);
        }
        byte[] key24 = new byte[24];
        System.arraycopy(key16, 0, key24, 0, 16);
        System.arraycopy(key16, 0, key24, 16, 8);
        return key24;
    }

    private static byte[] run(int mode, String algorithm, byte[] key, byte[] block) {
        if (block.length != BLOCK) {
            throw new IllegalArgumentException("DES works on 8-byte blocks, got " + block.length);
        }
        try {
            Cipher cipher = Cipher.getInstance(algorithm + "/ECB/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, algorithm));
            return cipher.doFinal(block);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(algorithm + " is part of every JDK", e);
        }
    }
}
