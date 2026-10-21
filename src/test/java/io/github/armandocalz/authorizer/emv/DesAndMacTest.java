package io.github.armandocalz.authorizer.emv;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class DesAndMacTest {

    private static final HexFormat HEX = HexFormat.of().withUpperCase();

    @Test
    void desMatchesThePublishedKnownAnswer() {
        // The classic DES example: key 0123456789ABCDEF, plaintext "Now is t"
        byte[] ciphertext = Des.encrypt(HEX.parseHex("0123456789ABCDEF"), HEX.parseHex("4E6F772069732074"));

        assertThat(HEX.formatHex(ciphertext)).isEqualTo("3FA40E8A984D4815");
        assertThat(Des.decrypt(HEX.parseHex("0123456789ABCDEF"), ciphertext)).isEqualTo(HEX.parseHex("4E6F772069732074"));
    }

    @Test
    void tripleDesWithEqualHalvesIsSingleDes() {
        byte[] k = HEX.parseHex("0123456789ABCDEF");
        byte[] block = HEX.parseHex("4E6F772069732074");

        assertThat(Des.tripleEncrypt(concat(k, k), block)).isEqualTo(Des.encrypt(k, block));
    }

    @Test
    void paddingMethod2AlwaysAddsAMarker() {
        assertThat(HEX.formatHex(Iso9797Mac.padMethod2(HEX.parseHex("0102")))).isEqualTo("0102800000000000");
        assertThat(HEX.formatHex(Iso9797Mac.padMethod2(HEX.parseHex("0102030405060708"))))
                .isEqualTo("0102030405060708" + "8000000000000000");
        assertThat(Iso9797Mac.padMethod2(new byte[0])).isEqualTo(HEX.parseHex("8000000000000000"));
    }

    @Test
    void macAlgorithm3MatchesAnIndependentJceComputation() throws Exception {
        Random random = new Random(9797);
        for (int length : new int[] {0, 1, 7, 8, 29, 64}) {
            byte[] key = new byte[16];
            byte[] data = new byte[length];
            random.nextBytes(key);
            random.nextBytes(data);

            // Independent path: JCE DES-CBC with K_L over every block but the last; the last block,
            // chained, then goes through JCE triple DES (K_L, K_R, K_L). Algorithm 3 is exactly that.
            byte[] padded = Iso9797Mac.padMethod2(data);
            byte[] kl = Arrays.copyOf(key, 8);
            byte[] kr = Arrays.copyOfRange(key, 8, 16);
            byte[] beforeLast = new byte[8];
            if (padded.length > 8) {
                Cipher cbc = Cipher.getInstance("DES/CBC/NoPadding");
                cbc.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(kl, "DES"), new IvParameterSpec(new byte[8]));
                byte[] out = cbc.doFinal(padded, 0, padded.length - 8);
                beforeLast = Arrays.copyOfRange(out, out.length - 8, out.length);
            }
            byte[] lastInput = xor(beforeLast, Arrays.copyOfRange(padded, padded.length - 8, padded.length));
            Cipher ede = Cipher.getInstance("DESede/ECB/NoPadding");
            ede.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(concat(concat(kl, kr), kl), "DESede"));

            assertThat(Iso9797Mac.algorithm3(key, data)).as("length %d", length).isEqualTo(ede.doFinal(lastInput));
        }
    }

    @Test
    void oddParityAdjustment() {
        byte[] adjusted = Des.withOddParity(HEX.parseHex("0000FFFE0102"));

        assertThat(HEX.formatHex(adjusted)).isEqualTo("0101FEFE0102");
        for (byte b : Des.withOddParity(new byte[] {0x12, 0x34, 0x56, 0x78})) {
            assertThat(Integer.bitCount(b & 0xFF) % 2).isEqualTo(1);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static byte[] xor(byte[] a, byte[] b) {
        byte[] out = new byte[a.length];
        for (int i = 0; i < a.length; i++) {
            out[i] = (byte) (a[i] ^ b[i]);
        }
        return out;
    }
}
